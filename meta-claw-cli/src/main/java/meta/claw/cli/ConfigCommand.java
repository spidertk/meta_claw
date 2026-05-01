package meta.claw.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "config", description = "Manage CLI configuration")
public class ConfigCommand implements Runnable {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".meta-claw");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yaml");

    @Parameters(index = "0", description = "Action: set, get, list")
    private String action;

    @Parameters(index = "1", arity = "0..1", description = "Key, e.g. providers.kimi.apiKey")
    private String key;

    @Parameters(index = "2", arity = "0..1", description = "Value")
    private String value;

    @Override
    public void run() {
        try {
            switch (action) {
                case "set" -> setConfig(key, value);
                case "get" -> getConfig(key);
                case "list" -> listConfig();
                default -> System.err.println("Unknown action: " + action);
            }
        } catch (IOException e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    private void setConfig(String key, String value) throws IOException {
        if (key == null || value == null) {
            System.err.println("Usage: meta-claw config set <key> <value>");
            return;
        }
        Files.createDirectories(CONFIG_DIR);
        String content = key + ": " + value + "\n";
        Files.writeString(CONFIG_FILE, content);
        System.out.println("Config saved: " + key);
    }

    private void getConfig(String key) throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            System.out.println("No config found.");
            return;
        }
        String content = Files.readString(CONFIG_FILE);
        for (String line : content.split("\n")) {
            if (line.startsWith(key + ":")) {
                System.out.println(line.split(":", 2)[1].trim());
                return;
            }
        }
        System.out.println("Key not found: " + key);
    }

    private void listConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            System.out.println("No config found.");
            return;
        }
        System.out.println(Files.readString(CONFIG_FILE));
    }
}
