package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;
import meta.claw.core.runtime.ExpertRuntime;
import meta.claw.core.runtime.SpringAiLlmClient;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.vessel.GlobalConfigLoader;
import meta.claw.vessel.VesselConfig;
import meta.claw.vessel.VesselConfigLoader;
import org.springframework.ai.chat.ChatClient;
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

    private final ChatClient chatClient;

    public ChatCommand(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Parameters(index = "0", defaultValue = "default", description = "Expert name")
    private String expertName;

    @Override
    public void run() {
        Path configDir = Paths.get(System.getProperty("user.home"), ".meta-claw");

        // 1. Load global config
        GlobalConfigLoader globalLoader = new GlobalConfigLoader();
        GlobalConfig globalConfig = globalLoader.load(configDir);

        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            System.err.println("Provider config not found.");
            System.err.println("Run 'meta-claw config set providers.<name>.api_key <key>' to set up.");
            return;
        }

        String providerName = globalConfig.getDefaultProvider();
        if (providerName == null || providerName.isBlank()) {
            providerName = globalConfig.getProviders().keySet().iterator().next();
        }

        ProviderConfig providerConfig = globalConfig.getProviders().get(providerName);
        if (providerConfig == null) {
            System.err.println("Provider not found: " + providerName);
            return;
        }

        String apiKey = providerConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "your-api-key".equals(apiKey)) {
            System.err.println("API key not set for provider '" + providerName + "'.");
            System.err.println("Run 'meta-claw config set providers." + providerName + ".api_key <your-key>' to configure.");
            return;
        }

        // 2. Load vessel config
        Path vesselPath = configDir.resolve("vessels").resolve(expertName).resolve("vessel.md");
        VesselConfigLoader vesselLoader = new VesselConfigLoader();
        VesselConfig vesselConfig = vesselLoader.loadSingle(vesselPath);

        if (vesselConfig == null) {
            System.err.println("Vessel not found: " + expertName);
            System.err.println("Run 'meta-claw init' to create default vessel.");
            return;
        }

        // 3. Determine model (vessel overrides provider)
        String model = vesselConfig.getModel();
        if (model == null || model.isBlank()) {
            model = providerConfig.getModel();
        }

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name(providerName)
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);
        ExpertRuntime runtime = new ExpertRuntime(null, chatClient);

        String displayName = vesselConfig.getName() != null ? vesselConfig.getName() : expertName;
        System.out.println("Chat with " + displayName + " (" + model + ")");
        System.out.println("Type /exit to quit.");
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
                SpiChatResponse response = llmClient.chat(request);
                System.out.println(response.content());
                System.out.println();

                history.add(SpiMessage.assistant(response.content()));
            }
        } catch (Exception e) {
            log.error("Chat error", e);
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("Goodbye!");
    }
}
