package meta.claw.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

@SpringBootApplication(scanBasePackages = {"meta.claw.cli", "meta.claw.core", "meta.claw.vessel"})
public class CliApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CliApplication.class);
        app.setAdditionalProfiles("cli");
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Bean
    CommandLineRunner run(CommandLine.IFactory factory, MetaClawCommand command) {
        return args -> {
            int exitCode = new CommandLine(command, factory).execute(args);
            System.exit(exitCode);
        };
    }
}
