package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.MemoryManager;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.SystemPromptBuilder;
import meta.claw.core.prompt.TemplateLoader;
import meta.claw.core.runtime.SpringAiLlmClient;
import meta.claw.core.spi.llm.LlmClientFactoryManager;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import meta.claw.core.spi.llm.SpiToolCall;
import meta.claw.vessel.ResolvedVesselConfig;
import meta.claw.core.model.VesselConfig;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselConfigResolver;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import meta.claw.core.session.ChatMessage;
import meta.claw.store.conversation.JsonlConversationStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Command(name = "chat", description = "Chat with a vessel")
public class ChatCommand implements Runnable {

    private final LlmClientFactoryManager factoryManager;
    private final VesselConfigResolver resolver;

    public ChatCommand(LlmClientFactoryManager factoryManager, VesselConfigResolver resolver) {
        this.factoryManager = factoryManager;
        this.resolver = resolver;
    }

    @Parameters(index = "0", defaultValue = "default", description = "Vessel name")
    private String vesselName;

    @Option(names = "--resume", description = "Resume an existing session id for this vessel")
    private String resumeSessionId;

    private JsonlConversationStore conversationStore;
    private String sessionKey;

    @Override
    public void run() {
        Terminal terminal;
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .build();
        } catch (IOException e) {
            System.err.println("Failed to initialize terminal: " + e.getMessage());
            return;
        }

        Path configDir = ProjectRootFinder.getMetaClawDir();
        ResolvedVesselConfig resolved;
        try {
            resolved = resolver.resolve(configDir, vesselName);
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        String providerName = resolved.getProviderName();
        meta.claw.core.model.ProviderConfig providerConfig = resolved.getProviderConfig();
        VesselConfig vesselConfig = resolved.getVesselConfig();

        String apiKey = providerConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "your-api-key".equals(apiKey)) {
            System.err.println("API key not set for provider '" + providerName + "'.");
            System.err.println("Run 'meta-claw config set providers." + providerName + ".api_key <your-key>' to configure.");
            return;
        }

        String model = providerConfig.getModel();
        if (model == null || model.isBlank()) {
            System.err.println("Model not set for provider '" + providerName + "'.");
            System.err.println("Run 'meta-claw config set providers." + providerName + ".model <model-name>' to configure.");
            return;
        }

        log.info("Using provider: {}", providerName);
        log.info("Provider config - baseUrl: {}, model: {}",
                providerConfig.getBaseUrl(),
                providerConfig.getModel());

        ChatClient chatClient = factoryManager.create(providerName, providerConfig);

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name(providerName)
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);

        // Initialize conversation store and session
        Path vesselsDir = configDir.resolve("vessels");
        this.conversationStore = new JsonlConversationStore(vesselsDir, vesselName);
        if (resumeSessionId != null && !resumeSessionId.isBlank()) {
            if (!conversationStore.conversationExists(resumeSessionId)) {
                System.err.println("Session not found for vessel '" + vesselName + "': " + resumeSessionId);
                return;
            }
            this.sessionKey = resumeSessionId;
        } else {
            this.sessionKey = UUID.randomUUID().toString();
        }

        String displayName = vesselConfig.getName() != null ? vesselConfig.getName() : vesselName;
        String emoji = vesselConfig.getEmoji() != null ? vesselConfig.getEmoji() : "🤖";
        String description = vesselConfig.getDescription() != null ? vesselConfig.getDescription() : "A general-purpose AI assistant.";

        // Welcome screen (inspired by vessel_cli/cli.py print_welcome)
        terminal.writer().println();
        terminal.writer().println("╔══════════════════════════════════════════════════════════════════╗");
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", emoji + "  " + displayName));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", description));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   Model: %-54s ║", model));
        terminal.writer().println(String.format("║   Provider: %-51s ║", providerName));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println("╚══════════════════════════════════════════════════════════════════╝");
        terminal.writer().println();
        terminal.writer().println("Commands: /exit  /clear");
        terminal.writer().println("Press Ctrl+D to quit");
        terminal.writer().println();
        terminal.flush();

        // Phase 2: Build dynamic system prompt via SystemPromptBuilder
        PromptContextFactory contextFactory = new PromptContextFactory();
        PromptContext promptContext = contextFactory.create(vesselConfig, configDir, null);
        TemplateLoader templateLoader = new TemplateLoader();
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(templateLoader);
        String systemPrompt = promptBuilder.build(promptContext);

        MemoryManager memoryManager = new MemoryManager();
        int maxHistoryRounds = vesselConfig.getMaxHistoryRounds() != null
                ? vesselConfig.getMaxHistoryRounds() : 20;

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<SpiMessage> history = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(SpiMessage.system(systemPrompt));
        }
        if (resumeSessionId != null && !resumeSessionId.isBlank()) {
            history.addAll(toSpiMessages(conversationStore.getHistory(sessionKey)));
        }

        try {
            while (true) {
                terminal.writer().print("> ");
                terminal.writer().flush();
                String input = reader.readLine();
                if (input == null || "/exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if ("/clear".equalsIgnoreCase(input.trim())) {
                    history.clear();
                    if (systemPrompt != null && !systemPrompt.isBlank()) {
                        history.add(SpiMessage.system(systemPrompt));
                    }
                    try {
                        conversationStore.clearHistory(sessionKey);
                    } catch (Exception e) {
                        log.warn("Failed to clear persisted history for session {}", sessionKey, e);
                    }
                    terminal.writer().println("History cleared.");
                    terminal.flush();
                    continue;
                }

                history.add(SpiMessage.user(input));

                // Persist user message
                try {
                    ChatMessage userMsg = ChatMessage.builder()
                            .sessionKey(sessionKey)
                            .role("user")
                            .content(input)
                            .vesselName(vesselName)
                            .timestamp(LocalDateTime.now())
                            .build();
                    conversationStore.appendMessage(sessionKey, userMsg);
                } catch (Exception e) {
                    log.error("Failed to persist user message", e);
                }

                // Phase 2: Truncate history before sending to LLM
                List<SpiMessage> truncatedHistory = memoryManager.truncateByRound(history, maxHistoryRounds);
                SpiChatRequest request = SpiChatRequest.builder().messages(truncatedHistory).build();

                terminal.writer().print("AI: ");
                terminal.writer().flush();
                StringBuilder responseBuffer = new StringBuilder();
                llmClient.chatStream(request, new SpiStreamingCallback() {
                    @Override
                    public void onStart() {
                        // no-op
                    }

                    @Override
                    public void onChunk(String chunk) {
                        terminal.writer().print(chunk);
                        terminal.writer().flush();
                        responseBuffer.append(chunk);
                    }

                    @Override
                    public void onToolCall(SpiToolCall toolCall) {
                        // no-op
                    }

                    @Override
                    public void onComplete(SpiChatResponse response) {
                        terminal.writer().println();
                        terminal.writer().flush();
                        String responseText = responseBuffer.toString();
                        history.add(SpiMessage.assistant(responseText));

                        // Persist assistant message
                        try {
                            ChatMessage assistantMsg = ChatMessage.builder()
                                    .sessionKey(sessionKey)
                                    .role("assistant")
                                    .content(responseText)
                                    .vesselName(vesselName)
                                    .timestamp(LocalDateTime.now())
                                    .build();
                            conversationStore.appendMessage(sessionKey, assistantMsg);
                        } catch (Exception e) {
                            log.error("Failed to persist assistant message", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        terminal.writer().println("\nError: " + error.getMessage());
                        terminal.writer().flush();
                    }
                });
            }
        } catch (Exception e) {
            log.error("Chat error", e);
            System.err.println("Error: " + e.getMessage());
        }

        terminal.writer().println("Goodbye!");
        terminal.writer().flush();
    }

    static List<SpiMessage> toSpiMessages(List<ChatMessage> messages) {
        List<SpiMessage> restored = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.getRole() == null) {
                continue;
            }
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(SpiMessage.assistant(message.getContent()));
                case "tool" -> restored.add(SpiMessage.tool(message.getContent()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }
}
