package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locates the Meta-Claw project root by walking up from {@code user.dir}
 * until it finds a directory containing {@code pom.xml}.
 * <p>
 * The {@code .meta-claw/} data directory is then placed under that project root,
 * keeping vessel configs, skills, and conversation data inside the project.
 * </p>
 */
@Slf4j
public final class ProjectRootFinder {

    private static final String MARKER_FILE = "pom.xml";
    private static final String DATA_DIR = ".meta-claw";

    private ProjectRootFinder() {
        // utility class
    }

    /**
     * Returns the {@code .meta-claw/} directory, resolved against the
     * nearest ancestor that contains {@code pom.xml}.
     *
     * @return absolute path to {@code <project-root>/.meta-claw}
     */
    public static Path getMetaClawDir() {
        return findProjectRoot().resolve(DATA_DIR);
    }

    /**
     * Walks up from {@code user.dir} looking for {@code pom.xml}.
     * If none is found, falls back to {@code user.dir} itself.
     */
    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

        while (current != null) {
            if (Files.exists(current.resolve(MARKER_FILE))) {
                log.debug("Project root found: {}", current);
                return current;
            }
            current = current.getParent();
        }

        Path fallback = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        log.warn("Could not find '{}' in any ancestor of user.dir. Falling back to: {}",
                MARKER_FILE, fallback);
        return fallback;
    }
}
