package meta.claw.cli;

import meta.claw.core.config.VesselConfig;
import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.memory.shortterm.ShortMemoryManager;
import meta.claw.store.memory.MemoryManagerFactory;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselConfigResolver;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Command(name = "sessions", description = "List sessions for a vessel")
public class SessionsCommand implements Runnable {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final VesselConfigResolver resolver;

    public SessionsCommand(VesselConfigResolver resolver) {
        this.resolver = resolver;
    }

    @Parameters(index = "0", defaultValue = "default", description = "Vessel name")
    private String vesselName;

    @Override
    public void run() {
        Path configDir = ProjectRootFinder.getMetaClawDir();
        try {
            var resolved = resolver.resolve(configDir, vesselName);
            VesselConfig config = resolved.getVesselConfig();
            MemoryManagerFactory memoryManagerFactory = new MemoryManagerFactory(configDir.resolve("vessels"));
            ShortMemoryManager memoryManager = memoryManagerFactory.createShortTerm(config.getMemory(), vesselName);
            printSessions(vesselName, memoryManager.listSessions(vesselName));
            return;
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

    }

    static void printSessions(String vesselName, List<MemoryEntry> sessions) {
        System.out.println("Sessions for vessel '" + vesselName + "'");
        System.out.println();
        if (sessions.isEmpty()) {
            System.out.println("No sessions found.");
            return;
        }
        System.out.println(String.format("%-36s  %-16s  %s", "SESSION ID", "UPDATED AT", "MESSAGES"));
        for (MemoryEntry session : sessions) {
            String updatedAt = session.getUpdatedAt() != null ? FORMATTER.format(session.getUpdatedAt()) : "";
            System.out.println(String.format("%-36s  %-16s  %d",
                    session.getSessionId(), updatedAt, session.getMessageCount()));
        }
    }
}
