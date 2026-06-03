package meta.claw.cli;

import meta.claw.core.runtime.VesselManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Scanner;

/**
 * 删除指定的 Vessel。
 * <p>
 * 通过 {@link VesselManager} 统一删除：销毁 Runtime、清内存、删目录。
 * 默认会要求用户确认，可使用 --yes 跳过确认。
 * </p>
 */
@Component
@Command(name = "delete", description = "Delete a vessel")
public class DeleteCommand implements Runnable {

    @Autowired
    private VesselManager vesselManager;

    @Parameters(index = "0", description = "Vessel name to delete")
    private String vesselName;

    @Option(names = {"--yes", "-y"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

    @Override
    public void run() {
        if (!vesselManager.hasVessel(vesselName)) {
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
            vesselManager.deleteVessel(vesselName);
            System.out.println("Deleted vessel: " + vesselName);
        } catch (Exception e) {
            System.err.println("Failed to delete vessel: " + e.getMessage());
        }
    }
}
