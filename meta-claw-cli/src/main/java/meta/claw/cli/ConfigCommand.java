package meta.claw.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "config", description = "Manage CLI configuration")
public class ConfigCommand implements Runnable {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".meta-claw");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yaml");
    private static final Pattern API_KEY_LINE_PATTERN = Pattern.compile("^(.*?api_key\\s*:\\s*)(.*)$");

    @Parameters(index = "0", description = "Action: set, get, list")
    private String action;

    @Parameters(index = "1", arity = "0..1", description = "Key, e.g. providers.moonshot.api_key")
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
        } catch (Exception e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    /* ---------- YAML helpers ---------- */

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYamlMap() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            Files.createDirectories(CONFIG_DIR);
            return new LinkedHashMap<>();
        }
        try (InputStream is = Files.newInputStream(CONFIG_FILE)) {
            Map<String, Object> map = new Yaml().load(is);
            return map != null ? map : new LinkedHashMap<>();
        }
    }

    private void saveYamlMap(Map<String, Object> map) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        String yaml = new Yaml(options).dump(map);
        Files.writeString(CONFIG_FILE, yaml);
    }

    /* ---------- set ---------- */

    private void setConfig(String key, String value) {
        if (key == null || value == null) {
            System.err.println("Usage: meta-cli config set <key> <value>");
            return;
        }
        try {
            Map<String, Object> config = loadYamlMap();
            String[] parts = key.split("\\.");
            Map<String, Object> current = config;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = current.get(parts[i]);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    current.put(parts[i], next);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nextMap = (Map<String, Object>) next;
                current = nextMap;
            }
            current.put(parts[parts.length - 1], parseValue(value));
            saveYamlMap(config);
            System.out.println("Config saved: " + key);
        } catch (Exception e) {
            System.err.println("Failed to set config: " + e.getMessage());
        }
    }

    private Object parseValue(String raw) {
        if (raw.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    /* ---------- get ---------- */

    @SuppressWarnings("unchecked")
    private void getConfig(String key) {
        if (key == null) {
            System.err.println("Usage: meta-cli config get <key>");
            return;
        }
        try {
            Map<String, Object> config = loadYamlMap();
            String[] parts = key.split("\\.");
            Object current = config;
            for (String part : parts) {
                if (!(current instanceof Map)) {
                    System.err.println("Key not found: " + key);
                    return;
                }
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
                if (current == null) {
                    System.err.println("Key not found: " + key);
                    return;
                }
            }

            if (current instanceof Map && parts.length == 2 && "providers".equals(parts[0])) {
                Map<String, Object> providerMap = (Map<String, Object>) current;
                providerMap.forEach((k, v) -> System.out.println(k + ": " + maskIfSensitive(k, v)));
            } else {
                System.out.println(maskIfSensitive(parts[parts.length - 1], current));
            }
        } catch (Exception e) {
            System.err.println("Failed to get config: " + e.getMessage());
        }
    }

    /* ---------- list ---------- */

    private void listConfig() {
        try {
            if (!Files.exists(CONFIG_FILE)) {
                System.out.println("No config found.");
                return;
            }
            String content = Files.readString(CONFIG_FILE);
            String masked = maskApiKeysInRawYaml(content);
            System.out.print(masked);
        } catch (Exception e) {
            System.err.println("Failed to list config: " + e.getMessage());
        }
    }

    private String maskApiKeysInRawYaml(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            Matcher matcher = API_KEY_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String rawValue = matcher.group(2).trim();
                if (rawValue.length() >= 2) {
                    char first = rawValue.charAt(0);
                    char last = rawValue.charAt(rawValue.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        rawValue = rawValue.substring(1, rawValue.length() - 1);
                    }
                }
                line = matcher.group(1) + mask(rawValue);
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /* ---------- masking ---------- */

    private String maskIfSensitive(String key, Object value) {
        if (value == null) {
            return "null";
        }
        if ("api_key".equals(key)) {
            return mask(value.toString());
        }
        return value.toString();
    }

    private String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
