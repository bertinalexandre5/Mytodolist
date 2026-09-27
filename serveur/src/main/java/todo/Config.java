package todo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Réglages du serveur.
 *
 * <p>Chaque réglage a une valeur par défaut, écrite dans {@link #DEFAULTS}. On peut la remplacer,
 * du moins prioritaire au plus prioritaire :
 *
 * <ol>
 *   <li>dans le fichier {@code .env} du dossier {@code serveur}, par une ligne {@code NOM=valeur} ;
 *   <li>par une variable d'environnement du même nom, pratique pour un essai ponctuel.
 * </ol>
 *
 * @param port port d'écoute (réglage PORT)
 * @param dbPath fichier de la base SQLite (DB_PATH)
 * @param demoData crée des listes d'exemple dans une base neuve (DEMO_DATA)
 * @param delayMs pause avant chaque réponse, pour simuler un réseau lent (DELAY_MS)
 */
public record Config(int port, String dbPath, boolean demoData, int delayMs) {

  /** Valeurs utilisées quand ni le fichier .env ni l'environnement ne les remplacent. */
  static final Map<String, String> DEFAULTS =
      Map.of(
          "PORT", "8080",
          "DB_PATH", "todo.db",
          "DEMO_DATA", "true",
          "DELAY_MS", "0");

  /**
   * Lit la configuration : les valeurs par défaut, puis celles du fichier .env, puis celles des
   * variables d'environnement. Chaque source remplace les valeurs de la précédente.
   *
   * @param envFile chemin du fichier .env (s'il n'existe pas, on s'en passe)
   * @param environment variables d'environnement, en général {@code System.getenv()}
   */
  public static Config load(Path envFile, Map<String, String> environment) throws IOException {
    Map<String, String> values = new HashMap<>(DEFAULTS);
    values.putAll(readEnvFile(envFile));
    for (String name : DEFAULTS.keySet()) {
      if (environment.containsKey(name)) {
        values.put(name, environment.get(name));
      }
    }
    return new Config(
        toInt(values, "PORT"),
        values.get("DB_PATH"),
        Boolean.parseBoolean(values.get("DEMO_DATA")),
        toInt(values, "DELAY_MS"));
  }

  /**
   * Lit un fichier .env : une ligne {@code NOM=valeur} par réglage. Les lignes vides et celles qui
   * commencent par {@code #} (des commentaires) sont ignorées, et les guillemets autour de la
   * valeur sont facultatifs. Un fichier absent donne simplement une liste vide.
   */
  static Map<String, String> readEnvFile(Path file) throws IOException {
    Map<String, String> values = new HashMap<>();
    if (!Files.exists(file)) {
      return values;
    }
    for (String line : Files.readAllLines(file)) {
      line = line.strip();
      int equals = line.indexOf('=');
      if (line.isEmpty() || line.startsWith("#") || equals < 0) {
        continue;
      }
      String name = line.substring(0, equals).strip();
      String value = removeQuotes(line.substring(equals + 1).strip());
      values.put(name, value);
    }
    return values;
  }

  /** "todo.db" ou 'todo.db' deviennent todo.db ; une valeur sans guillemets reste telle quelle. */
  private static String removeQuotes(String value) {
    boolean quoted =
        value.length() >= 2
            && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"));
    return quoted ? value.substring(1, value.length() - 1) : value;
  }

  private static int toInt(Map<String, String> values, String name) {
    try {
      return Integer.parseInt(values.get(name));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          name + " doit être un nombre entier, pas \"" + values.get(name) + "\".");
    }
  }
}
