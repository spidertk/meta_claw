package meta.claw.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

@SpringBootApplication(scanBasePackages = {"meta.claw.cli", "meta.claw.core", "meta.claw.export"})
public class CliApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(CliApplication.class, args)));
    }

    @Bean
    CommandLineRunner run(CommandLine.IFactory factory, MetaClawCommand command) {
        return args -> {
            int exitCode = new CommandLine(command, factory).execute(args);
            System.exit(exitCode);
        };
    }
}
