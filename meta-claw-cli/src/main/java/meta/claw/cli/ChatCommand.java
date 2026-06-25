package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.memory.shortterm.SessionSelection;



import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.VesselManager;
import meta.claw.core.runtime.VesselRuntime;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.tool.SpiToolCall;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.UUID;

@Slf4j
@Component
@Command(name = "chat", description = "Chat with a vessel")
public class ChatCommand implements Runnable {



    @Autowired
    private VesselManager vesselManager;

    @Autowired
    private Terminal terminal;

    @Autowired
    private LineReader lineReader;

    @Parameters(index = "0", defaultValue = "default", description = "Vessel name")
    private String vesselName;

    @Option(names = "--resume", description = "Resume an existing session id for this vessel")
    private String resumeSessionId;

    private String sessionId;

    @Override
    public void run() {
        VesselRuntime vesselRuntime = vesselManager.getRuntime(vesselName);
        meta.claw.core.runtime.VesselProfile profile = vesselRuntime.getProfile();
        if (profile == null || profile.getBundle() == null) {
            System.err.println("Vessel profile not loaded");
            return;
        }

        log.info("Using provider: {}", profile.getBundle().getProviderConfig());
        log.info("Provider config - baseUrl: {}, model: {}",
                profile.getBundle().getProviderConfig().getBaseUrl(),
                profile.getBundle().getProviderConfig().getModel());

        SessionSelection  sessionSelection;
        try {
            sessionSelection =vesselRuntime.getShortMemory().selectSession(vesselName,resumeSessionId, () -> UUID.randomUUID().toString());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }
        meta.claw.core.config.VesselConfig vesselConfig = profile.getBundle().getRuntimeVesselConfig();
        String displayName = vesselConfig != null && vesselConfig.getIdentity() != null && vesselConfig.getIdentity().getDisplayName() != null
                ? vesselConfig.getIdentity().getDisplayName()
                : (vesselConfig != null && vesselConfig.getIdentity() != null && vesselConfig.getIdentity().getName() != null
                        ? vesselConfig.getIdentity().getName() : vesselName);
        String emoji = vesselConfig != null && vesselConfig.getIdentity() != null && vesselConfig.getIdentity().getEmoji() != null
                ? vesselConfig.getIdentity().getEmoji() : "🤖";
        String description = vesselConfig != null && vesselConfig.getIdentity() != null && vesselConfig.getIdentity().getDescription() != null
                ? vesselConfig.getIdentity().getDescription() : "A general-purpose AI assistant.";

        terminal.writer().println();
        terminal.writer().println("╔══════════════════════════════════════════════════════════════════╗");
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", emoji + "  " + displayName));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   %-60s ║", description));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println(String.format("║   Model: %-54s ║", profile.getBundle().getProviderConfig().getModel()));
        terminal.writer().println(String.format("║   Provider: %-51s ║", profile.getBundle().getProviderConfig().getProvider()));
        terminal.writer().println("║                                                                  ║");
        terminal.writer().println("╚══════════════════════════════════════════════════════════════════╝");
        terminal.writer().println();
        terminal.writer().println("Session: " + sessionSelection.getSessionId());
        terminal.writer().println("History: " + sessionSelection.getHistoryFilePath());
        terminal.writer().println();
        terminal.writer().println("Commands: /exit  /clear");
        terminal.writer().println("Press Ctrl+D to quit");
        terminal.writer().println();
        terminal.flush();


        try {
            while (true) {
                String input = lineReader.readLine("> ");
                if (input == null || "/exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if ("/clear".equalsIgnoreCase(input.trim())) {

                    try {
                        vesselRuntime.getShortMemory().clearSession(vesselName, sessionSelection.getSessionId());
                    } catch (Exception e) {
                        log.warn("Failed to clear persisted history for session {}", sessionSelection.getSessionId(), e);
                    }
                    terminal.writer().println("History cleared.");
                    terminal.flush();
                    continue;
                }


                terminal.writer().println();
                terminal.writer().flush();

                long[] streamStartTime = {System.currentTimeMillis()};
                boolean[] hasReasoning = {false};
                boolean[] hasContent = {false};
                SpiUsage[] lastUsage = {null};

                vesselRuntime.chatStream(sessionSelection.getSessionId(),input, new SpiStreamingCallback() {
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
                    }

                    @Override
                    public ApprovalResolution onHitlSuspend(ApprovalTicket ticket) {
                        // 如果正在输出 thinking，先关闭灰色模式
                        if (hasReasoning[0] && !hasContent[0]) {
                            terminal.writer().println("\u001B[0m");
                            hasContent[0] = true;
                        }
                        HitlSubSystem hitlSub = vesselRuntime.getRegistry().get("hitl");
                        return hitlSub != null ? hitlSub.awaitResolution(ticket) : null;
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


}
