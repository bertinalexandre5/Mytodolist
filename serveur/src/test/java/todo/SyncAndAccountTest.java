package todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La synchronisation (GET et PUT /api/sync) et les comptes, testés contre un vrai serveur, sur un
 * port libre et avec une base en mémoire.
 */
class SyncAndAccountTest {

  private static final String ONE_LIST =
      "[{\"id\": 1, \"name\": \"  Week-end  \", \"tasks\": [{\"id\": 2, \"title\": \"Plein\", \"done\": true}]}]";

  private final HttpClient client = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private TodoServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = TodoServer.start(new Config(0, ":memory:", true, 0));
  }

  @AfterEach
  void stopServer() throws Exception {
    server.stop();
  }

  @Test
  void withoutAccountSyncReturnsStartingValuesAndSavesNothing() throws Exception {
    JsonNode starting = json.readTree(send("GET", "/api/sync", null, null).body());
    assertEquals(3, starting.size());
    assertEquals("Courses", starting.get(0).get("name").asString());

    assertEquals(204, send("PUT", "/api/sync", ONE_LIST, null).statusCode());

    // Rien n'a été enregistré : on retrouve les valeurs de départ.
    assertEquals(3, json.readTree(send("GET", "/api/sync", null, null).body()).size());
  }

  @Test
  void withAccountSyncSavesTheListsOfThatAccountOnly() throws Exception {
    String alice = register("alice", "motdepasse-alice");
    String bob = register("bob", "motdepasse-bob");

    assertEquals(3, json.readTree(send("GET", "/api/sync", null, alice).body()).size(), "rien d'enregistré");
    assertEquals(204, send("PUT", "/api/sync", ONE_LIST, alice).statusCode());

    JsonNode saved = json.readTree(send("GET", "/api/sync", null, alice).body());
    assertEquals(1, saved.size());
    assertEquals("Week-end", saved.get(0).get("name").asString(), "espaces retirés");
    assertTrue(saved.get(0).get("tasks").get(0).get("done").asBoolean());

    assertEquals(3, json.readTree(send("GET", "/api/sync", null, bob).body()).size(), "bob ne voit pas alice");
  }

  @Test
  void rejectsInvalidLists() throws Exception {
    String token = register("carole", "motdepasse-carole");
    String duplicateIds = "[{\"id\": 1, \"name\": \"A\", \"tasks\": []}, {\"id\": 1, \"name\": \"B\", \"tasks\": []}]";
    assertEquals(400, send("PUT", "/api/sync", duplicateIds, token).statusCode());
    assertEquals(400, send("PUT", "/api/sync", "[{\"id\": 1, \"name\": \" \", \"tasks\": []}]", token).statusCode());
    assertEquals(400, send("PUT", "/api/sync", "{\"pas\": \"un tableau\"}", token).statusCode());
  }

  @Test
  void registerLoginLogoutAndDeleteAccount() throws Exception {
    assertEquals(201, send("POST", "/api/accounts", credentials("david", "motdepasse-david"), null).statusCode());
    assertEquals(409, send("POST", "/api/accounts", credentials("DAVID", "autre-motdepasse"), null).statusCode());
    assertEquals(400, send("POST", "/api/accounts", credentials("d", "motdepasse-david"), null).statusCode());
    assertEquals(400, send("POST", "/api/accounts", credentials("emma", "court"), null).statusCode());

    assertEquals(401, send("POST", "/api/sessions", credentials("david", "mauvais-motdepasse"), null).statusCode());
    HttpResponse<String> login = send("POST", "/api/sessions", credentials("david", "motdepasse-david"), null);
    assertEquals(200, login.statusCode());
    String token = json.readTree(login.body()).get("token").asString();

    assertEquals(204, send("DELETE", "/api/sessions/current", null, token).statusCode());
    assertEquals(401, send("GET", "/api/sync", null, token).statusCode(), "jeton invalidé");

    String again = json.readTree(send("POST", "/api/sessions", credentials("david", "motdepasse-david"), null).body())
        .get("token").asString();
    assertEquals(204, send("DELETE", "/api/accounts/me", null, again).statusCode());
    assertEquals(401, send("POST", "/api/sessions", credentials("david", "motdepasse-david"), null).statusCode());
    assertEquals(401, send("DELETE", "/api/accounts/me", null, null).statusCode(), "connexion requise");
  }

  /** Crée un compte et renvoie son jeton de session. */
  private String register(String username, String password) throws Exception {
    HttpResponse<String> response = send("POST", "/api/accounts", credentials(username, password), null);
    assertEquals(201, response.statusCode());
    return json.readTree(response.body()).get("token").asString();
  }

  private static String credentials(String username, String password) {
    return "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
  }

  /** Envoie une requête ; body et token valent null quand il n'y en a pas. */
  private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
            .header("Content-Type", "application/json")
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return client.send(request.build(), BodyHandlers.ofString());
  }
}
