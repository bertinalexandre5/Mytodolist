package todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Chaque test démarre un vrai serveur, sur un port libre et avec une base vide en mémoire, puis
 * lui envoie des requêtes HTTP comme le ferait le navigateur.
 */
class TodoServerTest {

  private final HttpClient client = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private TodoServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = TodoServer.start(new Config(0, ":memory:", false, 0));
  }

  @AfterEach
  void stopServer() throws Exception {
    server.stop();
  }

  @Test
  void createsAListThenAddsAndChecksATask() throws Exception {
    HttpResponse<String> created = send("POST", "/api/lists", "{\"name\": \"  Courses  \"}");
    assertEquals(201, created.statusCode());
    JsonNode list = json.readTree(created.body());
    assertEquals("Courses", list.get("name").asString(), "espaces retirés autour du nom");
    assertEquals(0, list.get("tasks").size());

    long listId = list.get("id").asLong();
    HttpResponse<String> added = send("POST", "/api/lists/" + listId + "/tasks", "{\"title\": \"Pain\"}");
    assertEquals(201, added.statusCode());
    JsonNode task = json.readTree(added.body());
    assertEquals("Pain", task.get("title").asString());
    assertFalse(task.get("done").asBoolean());

    long taskId = task.get("id").asLong();
    HttpResponse<String> checked = send("PATCH", "/api/tasks/" + taskId, "{\"done\": true}");
    assertEquals(200, checked.statusCode());
    assertTrue(json.readTree(checked.body()).get("done").asBoolean());

    JsonNode lists = json.readTree(send("GET", "/api/lists", null).body());
    assertEquals(1, lists.size());
    assertTrue(lists.get(0).get("tasks").get(0).get("done").asBoolean());
  }

  @Test
  void deletingAListAlsoDeletesItsTasks() throws Exception {
    long listId = json.readTree(send("POST", "/api/lists", "{\"name\": \"Vacances\"}").body()).get("id").asLong();
    long taskId =
        json.readTree(send("POST", "/api/lists/" + listId + "/tasks", "{\"title\": \"Valise\"}").body())
            .get("id")
            .asLong();

    assertEquals(204, send("DELETE", "/api/lists/" + listId, null).statusCode());
    assertEquals("[]", send("GET", "/api/lists", null).body());
    assertEquals(404, send("DELETE", "/api/tasks/" + taskId, null).statusCode());
  }

  @Test
  void neverReusesTheNumberOfADeletedList() throws Exception {
    long first = json.readTree(send("POST", "/api/lists", "{\"name\": \"A\"}").body()).get("id").asLong();
    send("DELETE", "/api/lists/" + first, null);
    long second = json.readTree(send("POST", "/api/lists", "{\"name\": \"B\"}").body()).get("id").asLong();
    assertTrue(second > first);
  }

  @Test
  void rejectsInvalidRequests() throws Exception {
    assertEquals(400, send("POST", "/api/lists", "{\"name\": \"   \"}").statusCode());
    assertEquals(400, send("POST", "/api/lists", "pas du JSON").statusCode());
    assertEquals(400, send("POST", "/api/lists", "").statusCode());
    assertEquals(404, send("POST", "/api/lists/42/tasks", "{\"title\": \"Pain\"}").statusCode());
    assertEquals(404, send("PATCH", "/api/tasks/42", "{\"done\": true}").statusCode());
    assertEquals(404, send("GET", "/api/inconnue", null).statusCode());

    HttpResponse<String> error = send("POST", "/api/lists", "{}");
    assertEquals(400, error.statusCode());
    assertEquals("Le nom de la liste est obligatoire.", json.readTree(error.body()).get("error").asString());
  }

  @Test
  void answersHealthChecks() throws Exception {
    assertEquals("{\"status\":\"ok\"}", send("GET", "/api/health", null).body());
  }

  /** Envoie une requête HTTP au serveur de test ; body vaut null pour une requête sans corps. */
  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
            .header("Content-Type", "application/json")
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
            .build();
    return client.send(request, BodyHandlers.ofString());
  }
}
