package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.runtime.SpringAiLlmClient;
import meta.claw.core.spi.llm.LlmClientFactoryManager;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import meta.claw.core.spi.llm.SpiToolCall;
import meta.claw.vessel.ResolvedVesselConfig;
import meta.claw.vessel.VesselConfig;
import meta.claw.vessel.VesselConfigResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Command(name = "chat", description = "Chat with an expert")
public class ChatCommand implements Runnable {

    private final LlmClientFactoryManager factoryManager;

    public ChatCommand(LlmClientFactoryManager factoryManager) {
        this.factoryManager = factoryManager;
    }

    @Parameters(index = "0", defaultValue = "default", description = "Expert name")
    private String expertName;

    @Override
    public void run() {
        Path configDir = Paths.get(System.getProperty("user.dir"), ".meta-claw");
        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved;
        try {
            resolved = resolver.resolve(configDir, expertName);
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

        ChatClient chatClient = factoryManager.create(providerName, providerConfig, model);

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name(providerName)
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);

        String displayName = vesselConfig.getName() != null ? vesselConfig.getName() : expertName;
        String emoji = vesselConfig.getEmoji() != null ? vesselConfig.getEmoji() : "🤖";
        String description = vesselConfig.getDescription() != null ? vesselConfig.getDescription() : "A general-purpose AI assistant.";

        // Welcome screen (inspired by expert_cli/cli.py print_welcome)
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                  ║");
        System.out.println(String.format("║   %-60s ║", emoji + "  " + displayName));
        System.out.println("║                                                                  ║");
        System.out.println(String.format("║   %-60s ║", description));
        System.out.println("║                                                                  ║");
        System.out.println(String.format("║   Model: %-54s ║", model));
        System.out.println(String.format("║   Provider: %-51s ║", providerName));
        System.out.println("║                                                                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Commands: /exit  /clear");
        System.out.println("Press Ctrl+D to quit");
        System.out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<SpiMessage> history = new ArrayList<>();
        String systemPrompt = vesselConfig.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            history.add(SpiMessage.system(systemPrompt));
        }

        try {
            while (true) {
                System.out.print("> ");
                String input = reader.readLine();
                if (input == null || "/exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if ("/clear".equalsIgnoreCase(input.trim())) {
                    history.clear();
                    if (systemPrompt != null && !systemPrompt.isBlank()) {
                        history.add(SpiMessage.system(systemPrompt));
                    }
                    System.out.println("History cleared.");
                    continue;
                }

                history.add(SpiMessage.user(input));
                SpiChatRequest request = SpiChatRequest.builder().messages(history).build();

                System.out.print("AI: ");
                StringBuilder responseBuffer = new StringBuilder();
                llmClient.chatStream(request, new SpiStreamingCallback() {
                    @Override
                    public void onStart() {
                        // no-op
                    }

                    @Override
                    public void onChunk(String chunk) {
                        System.out.print(chunk);
                        System.out.flush();
                        responseBuffer.append(chunk);
                    }

                    @Override
                    public void onToolCall(SpiToolCall toolCall) {
                        // no-op
                    }

                    @Override
                    public void onComplete(SpiChatResponse response) {
                        System.out.println();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println("\nError: " + error.getMessage());
                    }
                });

                history.add(SpiMessage.assistant(responseBuffer.toString()));
            }
        } catch (Exception e) {
            log.error("Chat error", e);
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("Goodbye!");
    }
}
