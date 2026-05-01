package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ExpertConfig;
import meta.claw.core.runtime.ExpertRuntime;
import meta.claw.core.runtime.SpringAiLlmClient;
import meta.claw.core.spi.llm.*;
import meta.claw.export.ExpertConfigLoader;
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
        Path expertsDir = Paths.get(System.getProperty("user.home"), ".meta-claw", "experts");
        ExpertConfigLoader loader = new ExpertConfigLoader();
        ExpertConfig config = loader.loadSingle(expertsDir.resolve(expertName).resolve("expert.yaml"));

        if (config == null) {
            System.err.println("Expert not found: " + expertName);
            System.err.println("Run 'meta-claw init' to create default expert.");
            return;
        }

        ProviderMeta meta = ProviderMeta.builder()
                .name("kimi")
                .model(config.getModel())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);
        ExpertRuntime runtime = new ExpertRuntime(config, chatClient);

        System.out.println("Chat with " + config.getName() + " (" + config.getModel() + ")");
        System.out.println("Type /exit to quit.");
        System.out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<Message> history = new ArrayList<>();
        history.add(Message.system(config.getSystemPrompt()));

        try {
            while (true) {
                System.out.print("> ");
                String input = reader.readLine();
                if (input == null || "/exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if ("/clear".equalsIgnoreCase(input.trim())) {
                    history.clear();
                    history.add(Message.system(config.getSystemPrompt()));
                    System.out.println("History cleared.");
                    continue;
                }

                history.add(Message.user(input));
                ChatRequest request = ChatRequest.builder().messages(history).build();

                System.out.print("AI: ");
                ChatResponse response = llmClient.chat(request);
                System.out.println(response.content());
                System.out.println();

                history.add(Message.assistant(response.content()));
            }
        } catch (Exception e) {
            log.error("Chat error", e);
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("Goodbye!");
    }
}
