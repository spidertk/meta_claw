package meta.claw.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
    name = "meta-claw",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Meta-Claw AI Agent Platform CLI",
    subcommands = { InitCommand.class, ConfigCommand.class, ChatCommand.class, ListCommand.class, DeleteCommand.class }
)
public class MetaClawCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Meta-Claw CLI v1.0.0");
        System.out.println("Use --help for available commands.");
    }
}
