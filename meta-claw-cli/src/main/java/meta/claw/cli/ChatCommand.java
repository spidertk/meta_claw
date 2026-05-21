package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.shortterm.ShortMemoryManager;

import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextManager;
import meta.claw.core.prompt.SystemPromptBuilder;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.runtime.LlmClientManager;
import meta.claw.core.tool.SpiToolCall;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.UUID;

@Slf4j
@Component
@Command(name = "chat", description = "Chat with a vessel")
public class ChatCommand implements Runnable {

    @Autowired
    private ShortMemoryManager shortMemoryManager;
    @Autowired
    private  PromptContextManager promptContextManager;
    @Autowired
    private  SystemPromptBuilder promptBuilder;
    @Autowired
    private LlmClientManager llmClientManager;

    @Parameters(index = "0", defaultValue = "default", description = "Vessel name")
    private String vesselName;

    @Option(names = "--resume", description = "Resume an existing session id for this vessel")
    private String resumeSessionId;

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


        PromptContext baseCtx;
        try {
            baseCtx = promptContextManager.create(vesselName);
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }


        log.info("Using provider: {}", baseCtx.getProviderConfig());
        log.info("Provider config - baseUrl: {}, model: {}",
                baseCtx.getProviderConfig().getBaseUrl(),
                baseCtx.getProviderConfig().getModel());

        Path vesselsDir = baseCtx.getVesselsDir();
        Path historyFilePath;
        try {
            this.sessionKey = selectSession(vesselName, resumeSessionId, () -> UUID.randomUUID().toString());
            historyFilePath = historyFilePath(vesselsDir, vesselName, sessionKey);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }
        VesselConfig vesselConfig = baseCtx.getVesselConfig();

        String displayName = vesselConfig.getName() != null ? vesselConfig.getName() : vesselName;
        String emoji = vesselConfig.getEmoji() != null ? vesselConfig.getEmoji() : "🤖";
        String description = vesselConfig.getDescription() != null ? vesselConfig.getDescription() : "A general-purpose AI assistant.";

        terminal.writer().println();
        terminal.writer().println("╔══════════════════════════════════════════════════════════════════╗");
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", emoji + "  " + displayName));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", description));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   Model: %-54s ║", baseCtx.getProviderConfig().getModel()));
        terminal.writer().println(String.format("║   Provider: %-51s ║", baseCtx.getProviderConfig().getProvider()));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println("╚══════════════════════════════════════════════════════════════════╝");
        terminal.writer().println();
        terminal.writer().println("Session: " + sessionKey);
        terminal.writer().println("History: " + historyFilePath);
        terminal.writer().println();
        terminal.writer().println("Commands: /exit  /clear");
        terminal.writer().println("Press Ctrl+D to quit");
        terminal.writer().println();
        terminal.flush();

        String systemPrompt = promptBuilder.build(baseCtx);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<SpiMessage> history = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(SpiMessage.system(systemPrompt));
        }
        if (resumeSessionId != null && !resumeSessionId.isBlank()) {
            history.addAll(toSpiMessages(shortMemoryManager.getHistory(vesselName, sessionKey)));
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
                        shortMemoryManager.clearHistory(vesselName, sessionKey);
                    } catch (Exception e) {
                        log.warn("Failed to clear persisted history for session {}", sessionKey, e);
                    }
                    terminal.writer().println("History cleared.");
                    terminal.flush();
                    continue;
                }

                history.add(SpiMessage.user(input));
                try {
                    shortMemoryManager.appendMessage( vesselName, sessionKey,
                            MemoryMessageConverter.fromSpiMessage(SpiMessage.user(input)));
                } catch (Exception e) {
                    log.error("Failed to persist user message", e);
                }

                SpiChatRequest request = SpiChatRequest.builder()
                        .messages(llmClientManager.buildLlmRequest( vesselName, sessionKey, systemPrompt))
                        .build();

                terminal.writer().print("AI: ");
                terminal.writer().flush();
                StringBuilder responseBuffer = new StringBuilder();
                llmClientManager.chatStream(request, new SpiStreamingCallback() {
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
                        try {
                            shortMemoryManager.appendMessage(vesselName, sessionKey,
                                    MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant(responseText)));
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

    private List<SpiMessage> buildLlmRequest( String vesselId, String sessionKey, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }
        messages.addAll(toSpiMessages(shortMemoryManager.getHistory(vesselId, sessionKey)));
        return messages;
    }

    public List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.role() == null) {
                continue;
            }
            switch (message.role().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.content()));
                case "assistant" -> restored.add(SpiMessage.assistant(message.content()));
                case "tool" -> restored.add(SpiMessage.tool(message.content()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }

     String selectSession( String vesselName, String resumeSessionId,
                                Supplier<String> newSessionIds) {
        if (resumeSessionId != null && !resumeSessionId.isBlank()) {
            if (!shortMemoryManager.conversationExists( vesselName, resumeSessionId)) {
                throw new IllegalArgumentException("Session not found for vessel '" + vesselName + "': " + resumeSessionId);
            }
            return resumeSessionId;
        }

        String sessionId = newSessionIds.get();
        shortMemoryManager.initializeConversation( vesselName, sessionId);
        return sessionId;
    }

     Path historyFilePath(Path vesselsDir, String vesselName, String sessionKey) {
        return vesselsDir.resolve(vesselName).resolve("conversations").resolve(sessionKey).resolve("history.jsonl");
    }
}
