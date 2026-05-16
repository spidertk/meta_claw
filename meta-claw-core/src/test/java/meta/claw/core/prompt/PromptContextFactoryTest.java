package meta.claw.core.prompt;

import meta.claw.core.model.VesselConfig;
import meta.claw.core.memory.longterm.PreferenceEntry;
import meta.claw.core.memory.longterm.UserPreferenceStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptContextFactoryTest {

    private final PromptContextFactory factory = new PromptContextFactory();

    @Test
    void create_shouldMapVesselConfigFields() {
        VesselConfig config = new VesselConfig();
        config.setName("DevBot");
        config.setDescription("Coding assistant");
        config.setIdentity("You are a dev.");
        config.setSoul("Be precise.");
        config.setCapabilities("Write Java.");
        config.setGuidelines("No shortcuts.");
        config.setDomainKnowledge("Spring Boot.");

        PromptContext ctx = factory.create(config, Paths.get("/ws"), null);

        assertEquals("DevBot", ctx.getVesselName());
        assertEquals("Coding assistant", ctx.getVesselDescription());
        assertEquals("You are a dev.", ctx.getIdentity());
        assertEquals("Be precise.", ctx.getSoul());
        assertEquals("Write Java.", ctx.getCapabilities());
        assertEquals("No shortcuts.", ctx.getGuidelines());
        assertEquals("Spring Boot.", ctx.getKnowledge());
        assertEquals(Paths.get("/ws"), ctx.getWorkspaceDir());
    }

    @Test
    void create_shouldUseDefaultsForNullFields() {
        VesselConfig config = new VesselConfig();

        PromptContext ctx = factory.create(config, null, null);

        assertEquals("Vessel", ctx.getVesselName());
        assertEquals("", ctx.getVesselDescription());
        assertNotNull(ctx.getCurrentTime());
        assertNotNull(ctx.getLocation());
    }

    @Test
    void create_shouldLoadPreferences_whenEnabled() {
        VesselConfig config = new VesselConfig();
        config.setId("v1");
        config.setName("V");
        config.setPreferencesEnabled(true);

        UserPreferenceStore store = new UserPreferenceStore() {
            @Override
            public void addPreference(String vesselId, PreferenceEntry entry) {}
            @Override
            public List<PreferenceEntry> lookupPreference(String vesselId, String query) {
                return Collections.emptyList();
            }
            @Override
            public List<PreferenceEntry> listRecentPreferences(String vesselId, int limit) {
                return List.of(
                        PreferenceEntry.builder().content("Pref A").build(),
                        PreferenceEntry.builder().content("Pref B").build()
                );
            }
            @Override
            public boolean deletePreference(String vesselId, String preferenceId) {
                return false;
            }
            @Override
            public boolean clearPreferences(String vesselId) {
                return false;
            }
        };

        PromptContext ctx = factory.create(config, null, store);

        assertTrue(ctx.getPreferences().contains("Pref A"));
        assertTrue(ctx.getPreferences().contains("Pref B"));
    }

    @Test
    void create_shouldNotLoadPreferences_whenDisabled() {
        VesselConfig config = new VesselConfig();
        config.setId("v1");
        config.setName("V");
        config.setPreferencesEnabled(false);

        PromptContext ctx = factory.create(config, null, null);

        assertEquals("", ctx.getPreferences());
    }

    @Test
    void create_shouldContainCurrentTimeAndLocation() {
        VesselConfig config = new VesselConfig();
        config.setName("V");

        PromptContext ctx = factory.create(config, null, null);

        assertNotNull(ctx.getCurrentTime());
        assertFalse(ctx.getCurrentTime().isBlank());
        assertNotNull(ctx.getLocation());
        assertFalse(ctx.getLocation().isBlank());
    }
}
