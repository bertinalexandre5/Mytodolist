package todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {

  @TempDir Path folder;

  @Test
  void usesDefaultsWithoutEnvFile() throws Exception {
    Config config = Config.load(folder.resolve(".env"), Map.of());
    assertEquals(new Config(8080, "todo.db", true, 0), config);
  }

  @Test
  void envFileOverridesDefaults() throws Exception {
    Path env = folder.resolve(".env");
    Files.writeString(
        env,
        """
        # un commentaire
        PORT=9090

        DB_PATH = "autre.db"
        DEMO_DATA=false
        """);
    Config config = Config.load(env, Map.of());
    assertEquals(9090, config.port());
    assertEquals("autre.db", config.dbPath());
    assertFalse(config.demoData());
    assertEquals(0, config.delayMs(), "réglage absent du fichier : valeur par défaut");
  }

  @Test
  void environmentOverridesEnvFile() throws Exception {
    Path env = folder.resolve(".env");
    Files.writeString(env, "PORT=9090\nDELAY_MS=500\n");
    Config config = Config.load(env, Map.of("PORT", "7070"));
    assertEquals(7070, config.port());
    assertEquals(500, config.delayMs());
    assertTrue(config.demoData());
  }
}
