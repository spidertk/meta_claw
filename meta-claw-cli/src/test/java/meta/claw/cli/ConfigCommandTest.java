package meta.claw.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCommandTest {

    @Test
    void testConfigCommandStructure() {
        ConfigCommand cmd = new ConfigCommand();
        assertNotNull(cmd);
    }
}
