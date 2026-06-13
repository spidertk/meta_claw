package meta.claw.tool;

import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件系统操作工具。
 * <p>
 * 支持读、写、列目录和存在性检查。所有路径必须落在配置的 base-path 之下（默认为 JVM 启动目录），
 * 防止越权访问。可通过 {@code meta.claw.tool.file.enabled=false} 关闭。
 */
@ToolService
public class FileTool {

    @Value("${meta.claw.tool.file.enabled:true}")
    private boolean enabled = true;

    @Value("${meta.claw.tool.file.base-path:}")
    private String configuredBasePath;

    @Tool(description = "Read a text file and return its contents. Optionally limit the number of lines returned.")
    public String readFile(
            @ToolParam(description = "Relative or absolute file path") String path,
            @ToolParam(description = "Maximum number of lines to return (optional)", required = false) Integer maxLines) {
        if (!enabled) {
            return "Error: File tool is disabled by configuration";
        }
        try {
            Path target = resolvePath(path);
            if (!Files.isRegularFile(target)) {
                return "Error: not a regular file: " + target;
            }
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            if (maxLines != null && maxLines > 0 && lines.size() > maxLines) {
                lines = lines.subList(0, maxLines);
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Write text content to a file, creating parent directories if necessary.")
    public String writeFile(
            @ToolParam(description = "Relative or absolute file path") String path,
            @ToolParam(description = "Text content to write") String content) {
        if (!enabled) {
            return "Error: File tool is disabled by configuration";
        }
        if (content == null) {
            return "Error: content is null";
        }
        try {
            Path target = resolvePath(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return "OK: wrote " + target;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "List files and directories under the given directory.")
    public String listFiles(
            @ToolParam(description = "Relative or absolute directory path") String directory) {
        if (!enabled) {
            return "Error: File tool is disabled by configuration";
        }
        try {
            Path target = resolvePath(directory);
            if (!Files.isDirectory(target)) {
                return "Error: not a directory: " + target;
            }
            try (Stream<Path> stream = Files.list(target)) {
                return stream
                        .map(p -> (Files.isDirectory(p) ? "[D] " : "[F] ") + p.getFileName())
                        .sorted()
                        .collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Check whether a file or directory exists.")
    public String fileExists(
            @ToolParam(description = "Relative or absolute path") String path) {
        if (!enabled) {
            return "Error: File tool is disabled by configuration";
        }
        try {
            Path target = resolvePath(path);
            return String.valueOf(Files.exists(target));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private Path resolvePath(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("path is empty");
        }
        Path base = basePath();
        Path target = Path.of(input);
        if (!target.isAbsolute()) {
            target = base.resolve(target);
        }
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(base)) {
            throw new IllegalArgumentException("path escapes allowed base directory: " + base);
        }
        return normalized;
    }

    private Path basePath() {
        String base = configuredBasePath;
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.dir");
        }
        return Path.of(base).toAbsolutePath().normalize();
    }
}
