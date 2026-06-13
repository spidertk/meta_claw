package meta.claw.tool;

import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shell 命令执行工具。
 * <p>
 * 提供安全的本地 shell 执行能力，支持超时控制，返回 exit code、stdout、stderr。
 * 可通过 {@code meta.claw.tool.shell.enabled=false} 关闭。
 */
@ToolService
public class ShellTool {

    @Value("${meta.claw.tool.shell.enabled:true}")
    private boolean enabled = true;

    @Value("${meta.claw.tool.shell.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds = 30;

    @Tool(description = "Execute a shell command and return a JSON object with exitCode, stdout and stderr. "
            + "The command runs with a configurable timeout to prevent runaway processes.")
    public String execute(
            @ToolParam(description = "Shell command to execute. On Unix it runs as 'sh -c <command>', on Windows as 'cmd /c <command>'.") String command,
            @ToolParam(description = "Timeout in seconds (optional, default 30)", required = false) Integer timeoutSeconds) {
        if (!enabled) {
            return toJson(-1, "", "Shell tool is disabled by configuration");
        }
        if (command == null || command.isBlank()) {
            return toJson(-1, "", "Error: empty command");
        }
        int timeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : defaultTimeoutSeconds;
        try {
            return runCommand(command, timeout);
        } catch (Exception e) {
            return toJson(-1, "", "Error: " + e.getMessage());
        }
    }

    private String runCommand(String command, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = createProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        ExecutorService streamExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdoutFuture = streamExecutor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = streamExecutor.submit(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return toJson(-1, "", "Error: command timed out after " + timeoutSeconds + " seconds");
            }

            String stdout = getWithTimeout(stdoutFuture, timeoutSeconds);
            String stderr = getWithTimeout(stderrFuture, timeoutSeconds);
            return toJson(process.exitValue(), stdout, stderr);
        } catch (ExecutionException | TimeoutException e) {
            process.destroyForcibly();
            return toJson(-1, "", "Error: failed to read command output: " + e.getMessage());
        } finally {
            streamExecutor.shutdownNow();
        }
    }

    private ProcessBuilder createProcessBuilder(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd", "/c", command);
        }
        return new ProcessBuilder("sh", "-c", command);
    }

    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String getWithTimeout(Future<String> future, int timeoutSeconds)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private String toJson(int exitCode, String stdout, String stderr) {
        return String.format("{\"exitCode\":%d,\"stdout\":\"%s\",\"stderr\":\"%s\"}",
                exitCode, escapeJson(stdout), escapeJson(stderr));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
