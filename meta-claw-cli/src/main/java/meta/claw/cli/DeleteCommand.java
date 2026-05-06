package meta.claw.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * 删除指定的 Vessel。
 * <p>
 * 删除 .meta-claw/vessels/&lt;name&gt;/ 目录及其所有内容。
 * 默认会要求用户确认，可使用 --yes 跳过确认。
 * </p>
 */
@Component
@Command(name = "delete", description = "Delete a vessel")
public class DeleteCommand implements Runnable {

    @Parameters(index = "0", description = "Vessel name to delete")
    private String vesselName;

    @Option(names = {"--yes", "-y"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

    @Override
    public void run() {
        Path vesselDir = Paths.get(System.getProperty("user.dir"), ".meta-claw", "vessels", vesselName);

        if (!Files.exists(vesselDir)) {
            System.err.println("Vessel not found: " + vesselName);
            return;
        }

        if (!skipConfirm) {
            System.out.print("Are you sure you want to delete vessel '" + vesselName + "'? [y/N] ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim().toLowerCase();
            if (!input.equals("y") && !input.equals("yes")) {
                System.out.println("Cancelled.");
                return;
            }
        }

        try {
            deleteDirectory(vesselDir);
            System.out.println("Deleted vessel: " + vesselName);
        } catch (IOException e) {
            System.err.println("Failed to delete vessel: " + e.getMessage());
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
        }
    }
}
