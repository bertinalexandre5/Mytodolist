package todo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Accès à la base pour les comptes (étape 11) : toutes les requêtes SQL des comptes, des sessions
 * et des listes enregistrées sont dans cette classe.
 *
 * <p>Trois tables, sans lien avec celles des routes de test (TodoRepository) :
 *
 * <pre>
 * accounts    (id, username, salt, password_hash)  un compte ; jamais le mot de passe lui-même
 * sessions    (token_hash, account_id, expires_at) une connexion en cours
 * saved_lists (account_id, content)                les listes d'un compte, en JSON
 * </pre>
 *
 * <p>ON DELETE CASCADE : supprimer un compte supprime aussi ses sessions et ses listes.
 */
public final class AccountRepository implements AutoCloseable {

  /** Un compte, tel qu'enregistré. */
  record Account(long id, String username, byte[] salt, byte[] passwordHash) {}

  // COLLATE NOCASE : « Alice » et « alice » sont le même identifiant.
  private static final String CREATE_ACCOUNTS =
      """
      CREATE TABLE IF NOT EXISTS accounts (
        id            INTEGER PRIMARY KEY AUTOINCREMENT,
        username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
        salt          BLOB NOT NULL,
        password_hash BLOB NOT NULL
      )""";

  // expires_at : date d'expiration, en secondes depuis le 1er janvier 1970.
  private static final String CREATE_SESSIONS =
      """
      CREATE TABLE IF NOT EXISTS sessions (
        token_hash BLOB PRIMARY KEY,
        account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
        expires_at INTEGER NOT NULL
      )""";

  // Les listes sont enregistrées d'un bloc, sous forme de texte JSON : une ligne par compte.
  private static final String CREATE_SAVED_LISTS =
      """
      CREATE TABLE IF NOT EXISTS saved_lists (
        account_id INTEGER PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
        content    TEXT NOT NULL
      )""";

  private final Connection connection;

  /** Ouvre la base (la même que TodoRepository) et crée les tables des comptes au besoin. */
  public AccountRepository(String path) throws SQLException {
    connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute(CREATE_ACCOUNTS);
      statement.execute(CREATE_SESSIONS);
      statement.execute(CREATE_SAVED_LISTS);
    }
  }

  /** Crée un compte et renvoie son numéro, ou « rien » si l'identifiant est déjà pris. */
  public Optional<Long> createAccount(String username, byte[] salt, byte[] passwordHash)
      throws SQLException {
    // ON CONFLICT … DO NOTHING : si l'identifiant existe déjà, l'insertion n'a pas lieu et
    // RETURNING ne renvoie aucune ligne.
    try (PreparedStatement insert =
            prepare(
                "INSERT INTO accounts (username, salt, password_hash) VALUES (?, ?, ?)"
                    + " ON CONFLICT (username) DO NOTHING RETURNING id",
                username,
                salt,
                passwordHash);
        ResultSet row = insert.executeQuery()) {
      return row.next() ? Optional.of(row.getLong("id")) : Optional.empty();
    }
  }

  /** Le compte qui porte cet identifiant (sans distinction de majuscules), s'il existe. */
  public Optional<Account> findAccount(String username) throws SQLException {
    try (PreparedStatement select =
            prepare("SELECT id, username, salt, password_hash FROM accounts WHERE username = ?", username);
        ResultSet row = select.executeQuery()) {
      if (!row.next()) {
        return Optional.empty();
      }
      return Optional.of(
          new Account(
              row.getLong("id"),
              row.getString("username"),
              row.getBytes("salt"),
              row.getBytes("password_hash")));
    }
  }

  /** Supprime un compte, avec ses sessions et ses listes (ON DELETE CASCADE). */
  public void deleteAccount(long accountId) throws SQLException {
    update("DELETE FROM accounts WHERE id = ?", accountId);
  }

  /** Enregistre une nouvelle session (seulement l'empreinte de son jeton). */
  public void createSession(byte[] tokenHash, long accountId, long expiresAt) throws SQLException {
    update(
        "INSERT INTO sessions (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
        tokenHash,
        accountId,
        expiresAt);
  }

  /** Le numéro du compte d'une session encore valable, ou « rien ». */
  public Optional<Long> findSessionAccount(byte[] tokenHash, long now) throws SQLException {
    try (PreparedStatement select =
            prepare(
                "SELECT account_id FROM sessions WHERE token_hash = ? AND expires_at > ?",
                tokenHash,
                now);
        ResultSet row = select.executeQuery()) {
      return row.next() ? Optional.of(row.getLong("account_id")) : Optional.empty();
    }
  }

  /** Supprime une session : son jeton ne vaut plus rien (déconnexion). */
  public void deleteSession(byte[] tokenHash) throws SQLException {
    update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
  }

  /** Fait le ménage : supprime les sessions expirées. */
  public void deleteExpiredSessions(long now) throws SQLException {
    update("DELETE FROM sessions WHERE expires_at <= ?", now);
  }

  /** Les listes enregistrées d'un compte (texte JSON), ou « rien » s'il n'a jamais rien envoyé. */
  public Optional<String> findSavedLists(long accountId) throws SQLException {
    try (PreparedStatement select =
            prepare("SELECT content FROM saved_lists WHERE account_id = ?", accountId);
        ResultSet row = select.executeQuery()) {
      return row.next() ? Optional.of(row.getString("content")) : Optional.empty();
    }
  }

  /** Enregistre les listes d'un compte, à la place des précédentes. */
  public void saveLists(long accountId, String content) throws SQLException {
    // ON CONFLICT … DO UPDATE : la ligne du compte est créée la première fois, remplacée ensuite.
    update(
        "INSERT INTO saved_lists (account_id, content) VALUES (?, ?)"
            + " ON CONFLICT (account_id) DO UPDATE SET content = excluded.content",
        accountId,
        content);
  }

  private void update(String sql, Object... parameters) throws SQLException {
    try (PreparedStatement statement = prepare(sql, parameters)) {
      statement.executeUpdate();
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
