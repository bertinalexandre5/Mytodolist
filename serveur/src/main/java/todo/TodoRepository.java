package todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Accès à la base de données SQLite : toutes les requêtes SQL du serveur sont dans cette classe.
 *
 * <p>La base contient deux tables :
 *
 * <pre>
 * todo_lists (id, name)                  une ligne par liste
 * tasks      (id, list_id, title, done)  une ligne par tâche ; list_id est le numéro de sa liste
 * </pre>
 *
 * <p>Les valeurs venant du navigateur ne sont jamais collées dans le texte SQL : elles passent par
 * les « ? » d'une requête préparée ({@link PreparedStatement}), ce qui empêche l'injection SQL.
 */
public final class TodoRepository implements AutoCloseable {

  // AUTOINCREMENT : un numéro n'est jamais réutilisé, même après une suppression. Ainsi, un
  // onglet resté ouvert sur une ancienne tâche ne peut pas toucher, par erreur, une tâche
  // créée depuis qui aurait récupéré son numéro.
  private static final String CREATE_LISTS =
      """
      CREATE TABLE IF NOT EXISTS todo_lists (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL
      )""";

  // done vaut 0 (à faire) ou 1 (réalisée) : SQLite n'a pas de vrai type booléen.
  // ON DELETE CASCADE : quand on supprime une liste, la base supprime aussi ses tâches.
  private static final String CREATE_TASKS =
      """
      CREATE TABLE IF NOT EXISTS tasks (
        id      INTEGER PRIMARY KEY AUTOINCREMENT,
        list_id INTEGER NOT NULL REFERENCES todo_lists (id) ON DELETE CASCADE,
        title   TEXT NOT NULL,
        done    INTEGER NOT NULL DEFAULT 0
      )""";

  private final Connection connection;
  private final boolean isNew;

  /**
   * Ouvre la base (et la crée si le fichier n'existe pas encore).
   *
   * @param path fichier de la base, ou {@code :memory:} pour une base en mémoire (tests)
   */
  public TodoRepository(String path) throws SQLException {
    isNew = path.equals(":memory:") || !Files.exists(Path.of(path));
    connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (Statement statement = connection.createStatement()) {
      // SQLite n'applique les liens entre tables (et donc ON DELETE CASCADE) que si on le demande.
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute(CREATE_LISTS);
      statement.execute(CREATE_TASKS);
    }
  }

  /** Vrai si la base vient d'être créée, c'est-à-dire au tout premier lancement. */
  public boolean isNew() {
    return isNew;
  }

  /** Toutes les listes, dans l'ordre de création, chacune avec ses tâches. */
  public List<TodoList> findAllLists() throws SQLException {
    // 1. On lit toutes les tâches et on les range par numéro de liste.
    Map<Long, List<Task>> tasksByList = new HashMap<>();
    try (PreparedStatement select =
            prepare("SELECT id, list_id, title, done FROM tasks ORDER BY id");
        ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        Task task = new Task(rows.getLong("id"), rows.getString("title"), rows.getBoolean("done"));
        tasksByList.computeIfAbsent(rows.getLong("list_id"), listId -> new ArrayList<>()).add(task);
      }
    }
    // 2. On lit toutes les listes et on donne à chacune ses tâches (aucune si elle est vide).
    List<TodoList> lists = new ArrayList<>();
    try (PreparedStatement select = prepare("SELECT id, name FROM todo_lists ORDER BY id");
        ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        long id = rows.getLong("id");
        lists.add(new TodoList(id, rows.getString("name"), tasksByList.getOrDefault(id, List.of())));
      }
    }
    return lists;
  }

  /** Crée une liste vide et la renvoie avec le numéro que la base lui a attribué. */
  public TodoList createList(String name) throws SQLException {
    long id = insert("INSERT INTO todo_lists (name) VALUES (?) RETURNING id", name);
    return new TodoList(id, name, List.of());
  }

  /** Supprime une liste et ses tâches ; renvoie faux si elle n'existait pas. */
  public boolean deleteList(long id) throws SQLException {
    try (PreparedStatement delete = prepare("DELETE FROM todo_lists WHERE id = ?", id)) {
      return delete.executeUpdate() > 0;
    }
  }

  /** Ajoute une tâche, non réalisée, à une liste ; renvoie « rien » si la liste n'existe pas. */
  public Optional<Task> createTask(long listId, String title) throws SQLException {
    try (PreparedStatement select = prepare("SELECT id FROM todo_lists WHERE id = ?", listId);
        ResultSet list = select.executeQuery()) {
      if (!list.next()) {
        return Optional.empty();
      }
    }
    long id = insert("INSERT INTO tasks (list_id, title) VALUES (?, ?) RETURNING id", listId, title);
    return Optional.of(new Task(id, title, false));
  }

  /** Coche ou décoche une tâche ; renvoie « rien » si elle n'existe pas. */
  public Optional<Task> updateTask(long id, boolean done) throws SQLException {
    // RETURNING : la base renvoie la tâche telle qu'elle est après la modification.
    try (PreparedStatement update =
            prepare("UPDATE tasks SET done = ? WHERE id = ? RETURNING id, title, done", done, id);
        ResultSet row = update.executeQuery()) {
      if (!row.next()) {
        return Optional.empty();
      }
      return Optional.of(new Task(row.getLong("id"), row.getString("title"), row.getBoolean("done")));
    }
  }

  /** Supprime une tâche ; renvoie faux si elle n'existait pas. */
  public boolean deleteTask(long id) throws SQLException {
    try (PreparedStatement delete = prepare("DELETE FROM tasks WHERE id = ?", id)) {
      return delete.executeUpdate() > 0;
    }
  }

  /** Remplit une base neuve avec les listes d'exemple (voir StartingLists). */
  public void insertDemoData() throws SQLException {
    for (TodoList list : StartingLists.LISTS) {
      long listId = createList(list.name()).id();
      for (Task task : list.tasks()) {
        long taskId = createTask(listId, task.title()).orElseThrow().id();
        if (task.done()) {
          updateTask(taskId, true);
        }
      }
    }
  }

  /** Exécute un INSERT … RETURNING id et renvoie le numéro attribué par la base. */
  private long insert(String sql, Object... parameters) throws SQLException {
    try (PreparedStatement insert = prepare(sql, parameters);
        ResultSet row = insert.executeQuery()) {
      row.next();
      return row.getLong("id");
    }
  }

  /** Prépare une requête SQL : chaque « ? » est remplacé par un paramètre, dans l'ordre. */
  private PreparedStatement prepare(String sql, Object... parameters) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    for (int i = 0; i < parameters.length; i++) {
      statement.setObject(i + 1, parameters[i]);
    }
    return statement;
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
