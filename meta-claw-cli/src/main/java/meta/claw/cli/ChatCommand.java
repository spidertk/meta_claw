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
import meta.claw.core.llm.SpiUsage;
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
                        .vesselName(vesselName)
                        .build();

                terminal.writer().println();
                terminal.writer().flush();
                StringBuilder responseBuffer = new StringBuilder();
                StringBuilder reasoningBuffer = new StringBuilder();
                long[] streamStartTime = {System.currentTimeMillis()};
                boolean[] hasReasoning = {false};
                boolean[] hasContent = {false};
                SpiUsage[] lastUsage = {null};

                llmClientManager.chatStream(request, new SpiStreamingCallback() {
                    @Override
                    public void onStart() {
                        // no-op
                    }

                    @Override
                    public void onReasoningChunk(String chunk) {
                        if (!hasReasoning[0]) {
                            hasReasoning[0] = true;
                            terminal.writer().println("\u001B[90m🤔 Thinking...\u001B[0m");
                            terminal.writer().print("\u001B[90m  ");
                            terminal.writer().flush();
                        }
                        terminal.writer().print(chunk);
                        terminal.writer().flush();
                        reasoningBuffer.append(chunk);
                    }

                    @Override
                    public void onChunk(String chunk) {
                        if (hasReasoning[0] && !hasContent[0]) {
                            hasContent[0] = true;
                            terminal.writer().println("\u001B[0m"); // 结束灰色
                            terminal.writer().println();
                            terminal.writer().print("💡 ");
                            terminal.writer().flush();
                        }
                        if (!hasContent[0]) {
                            hasContent[0] = true;
                            terminal.writer().print("💡 ");
                            terminal.writer().flush();
                        }
                        terminal.writer().print(chunk);
                        terminal.writer().flush();
                        responseBuffer.append(chunk);
                    }

                    @Override
                    public void onToolCall(SpiToolCall toolCall) {
                        log.debug(String.format("onToolCall function:%s,arguments:%s ", toolCall.getName(), toolCall.getArguments()));
                        // 如果正在输出 thinking，先关闭灰色模式
                        if (hasReasoning[0] && !hasContent[0]) {
                            terminal.writer().println("\u001B[0m");
                            hasContent[0] = true; // 标记已退出 thinking 模式
                        }
                        terminal.writer().printf("\u001B[36m🔧 Calling tool: %s(%s)\u001B[0m%n",
                                toolCall.getName(),
                                toolCall.getArguments() != null ? toolCall.getArguments() : "{}");
                        terminal.writer().flush();
                    }

                    @Override
                    public void onUsage(SpiUsage usage) {
                        lastUsage[0] = usage;
                    }

                    @Override
                    public void onComplete(SpiChatResponse response) {
                        long totalTime = System.currentTimeMillis() - streamStartTime[0];

                        // 如果只有 reasoning 没有 content，需要关闭灰色模式并换行
                        if (hasReasoning[0] && !hasContent[0]) {
                            terminal.writer().println("\u001B[0m");
                        }
                        terminal.writer().println();

                        // 打印 usage 和执行时间
                        if (lastUsage[0] != null) {
                            terminal.writer().printf("\u001B[90m⏱️ %.1fs | 🔤 %d tokens (prompt: %d, completion: %d)\u001B[0m%n",
                                    totalTime / 1000.0,
                                    lastUsage[0].totalTokens() != null ? lastUsage[0].totalTokens() : 0,
                                    lastUsage[0].promptTokens() != null ? lastUsage[0].promptTokens() : 0,
                                    lastUsage[0].completionTokens() != null ? lastUsage[0].completionTokens() : 0);
                        } else {
                            terminal.writer().printf("\u001B[90m⏱️ %.1fs\u001B[0m%n", totalTime / 1000.0);
                        }
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
                        terminal.writer().println("\n\u001B[91mError: " + error.getMessage() + "\u001B[0m");
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
