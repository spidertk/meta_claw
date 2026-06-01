package meta.claw.core.user;

import meta.claw.core.exception.VesselException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselProfileLoaderTest {

    @TempDir
    Path tempDir;

    private final VesselProfileLoader loader = new VesselProfileLoader();

    @Test
    void load_throwsWhenFileMissing() {
        assertThrows(VesselException.class, () -> loader.load(tempDir.resolve("nonexistent")));
    }

    @Test
    void load_parsesSections() throws Exception {
        String md = "## Identity\n\nI am a code review assistant.\n\n## Soul\n\nPrecise.\n";
        Path vesselDir = tempDir.resolve("vessel");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.profile.md"), md);

        VesselProfile profile = loader.load(vesselDir);
        assertEquals("I am a code review assistant.", profile.getIdentity());
        assertEquals("Precise.", profile.getSoul());
    }
}
