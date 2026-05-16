package meta.claw.cli;

import meta.claw.core.config.GlobalConfigLoader;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.core.config.GlobalConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

@SpringBootApplication(
        scanBasePackages = {"meta.claw.cli", "meta.claw.core", "meta.claw.vessel"},
        exclude = {
                org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class
        }
)
public class CliApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CliApplication.class);
        app.setAdditionalProfiles("cli");

        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            Path configDir = ProjectRootFinder.getMetaClawDir();
            GlobalConfigLoader loader = new GlobalConfigLoader();
            GlobalConfig config = loader.load(configDir);

            LoggingSystem loggingSystem = LoggingSystem.get(CliApplication.class.getClassLoader());
            if (config != null && Boolean.TRUE.equals(config.getLogDebug())) {
                loggingSystem.setLogLevel("meta.claw", LogLevel.DEBUG);
                System.err.println("[CliApplication] log.debug=true, meta.claw 日志级别已设为 DEBUG");
            } else {
                loggingSystem.setLogLevel("meta.claw", LogLevel.INFO);
                System.err.println("[CliApplication] log.debug 未启用，meta.claw 日志级别保持 INFO");
            }
        });

        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Bean
    CommandLineRunner run(CommandLine.IFactory factory, MetaClawCommand command) {
        return args -> {
            // 防御：shell wrapper 或 IDE 可能把命令名也作为参数传入
            String[] effectiveArgs = args;
            if (args.length > 0 && "vessel-cli".equals(args[0])) {
                effectiveArgs = Arrays.copyOfRange(args, 1, args.length);
            }
            int exitCode = new CommandLine(command, factory).execute(effectiveArgs);
            System.exit(exitCode);
        };
    }

}
