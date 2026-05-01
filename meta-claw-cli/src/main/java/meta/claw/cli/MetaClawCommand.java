package meta.claw.cli;

import picocli.CommandLine.Command;

@Command(
    name = "meta-claw",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Meta-Claw AI Agent Platform CLI",
    subcommands = { InitCommand.class, ConfigCommand.class, ChatCommand.class }
)
public class MetaClawCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Meta-Claw CLI v1.0.0");
        System.out.println("Use --help for available commands.");
    }
}
