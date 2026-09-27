package todo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serveur HTTP de la TodoList. Il reçoit les requêtes envoyées par l'application Angular, lit ou
 * modifie la base SQLite, puis répond en JSON.
 *
 * <p>Toutes les adresses commencent par {@code /api} :
 *
 * <pre>
 * GET    /api/lists              toutes les listes, avec leurs tâches
 * POST   /api/lists              créer une liste         corps : {"name": "Courses"}
 * DELETE /api/lists/{id}         supprimer une liste (et ses tâches)
 * POST   /api/lists/{id}/tasks   ajouter une tâche       corps : {"title": "Pain"}
 * PATCH  /api/tasks/{id}         cocher ou décocher      corps : {"done": true}
 * DELETE /api/tasks/{id}         supprimer une tâche
 * GET    /api/health             vérifier que le serveur répond
 * </pre>
 *
 * <p>Il s'appuie sur le petit serveur HTTP fourni avec le JDK, qui traite les requêtes une par une :
 * c'est largement suffisant pour un usage sur son propre poste.
 */
public final class TodoServer {

  private static final int MAX_NAME_LENGTH = 100;
  private static final int MAX_TITLE_LENGTH = 200;
  private static final int MAX_BODY_BYTES = 10_000;

  /** Un numéro dans une adresse, par exemple le 12 de /api/tasks/12. */
  private static final Pattern ID = Pattern.compile("/(\\d{1,18})(?=/|$)");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  // Forme du JSON attendu dans le corps des requêtes. Un champ absent vaut null.
  private record NewList(String name) {}

  private record NewTask(String title) {}

  private record TaskChange(Boolean done) {}

  private final HttpServer http;
  private final TodoRepository repository;
  private final Config config;

  /** Convertit les objets Java en JSON et le JSON en objets Java. */
  private final JsonMapper json = JsonMapper.builder().build();

  private TodoServer(HttpServer http, TodoRepository repository, Config config) {
    this.http = http;
    this.repository = repository;
    this.config = config;
  }

  /** Point d'entrée : lit la configuration puis démarre le serveur. */
  public static void main(String[] args) throws Exception {
    Config config = Config.load(Path.of(".env"), System.getenv());
    try {
      TodoServer server = start(config);
      System.out.println("Serveur TodoList : http://localhost:" + server.port() + "/api/lists");
      System.out.println("Base de données  : " + Path.of(config.dbPath()).toAbsolutePath());
      System.out.println("Ctrl+C pour arrêter. Requêtes reçues :");
    } catch (BindException e) {
      System.err.println(
          "Le port " + config.port() + " est déjà utilisé : le serveur tourne peut-être déjà dans"
              + " un autre terminal. Sinon, choisissez un autre PORT dans le fichier .env.");
      System.exit(1);
    }
  }

  /**
   * Ouvre la base puis démarre le serveur HTTP. Il n'écoute que sur la machine locale (localhost) :
   * les autres ordinateurs du réseau ne peuvent pas l'atteindre.
   */
  public static TodoServer start(Config config) throws IOException, SQLException {
    TodoRepository repository = new TodoRepository(config.dbPath());
    if (config.demoData() && repository.isNew()) {
      repository.insertDemoData();
    }
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), config.port());
    TodoServer server = new TodoServer(HttpServer.create(address, 0), repository, config);
    // Toute requête dont l'adresse commence par /api/ est confiée à la méthode handle.
    server.http.createContext("/api/", server::handle);
    server.http.start();
    return server;
  }

  /** Port réellement utilisé (utile quand on demande le port 0, « n'importe lequel de libre »). */
  public int port() {
    return http.getAddress().getPort();
  }

  public void stop() throws SQLException {
    http.stop(0);
    repository.close();
  }

  /** Traite une requête et garantit qu'une réponse part toujours, même en cas d'erreur. */
  private void handle(HttpExchange exchange) throws IOException {
    try {
      if (config.delayMs() > 0) {
        Thread.sleep(config.delayMs());
      }
      route(exchange);
    } catch (ApiException e) {
      // Erreur prévue (donnée invalide, liste introuvable…) : on explique le problème.
      send(exchange, e.status(), Map.of("error", e.getMessage()));
    } catch (JacksonException e) {
      send(exchange, 400, Map.of("error", "Le corps de la requête n'est pas un JSON valide."));
    } catch (Exception e) {
      // Erreur imprévue : le détail s'affiche dans la console du serveur, pas dans le navigateur.
      e.printStackTrace();
      send(exchange, 500, Map.of("error", "Erreur interne du serveur."));
    } finally {
      exchange.close();
    }
  }

  /** Choisit quoi faire selon la méthode HTTP (GET, POST…) et l'adresse de la requête. */
  private void route(HttpExchange exchange) throws Exception {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();

    // On repère le numéro éventuel de l'adresse puis on le remplace par {id} :
    // « PATCH /api/tasks/12 » devient « PATCH /api/tasks/{id} », avec id = 12.
    Matcher number = ID.matcher(path);
    long id = number.find() ? Long.parseLong(number.group(1)) : 0;
    String route = method + " " + ID.matcher(path).replaceAll("/{id}");

    switch (route) {
      case "GET /api/health" -> send(exchange, 200, Map.of("status", "ok"));
      case "GET /api/lists" -> send(exchange, 200, repository.findAllLists());
      case "POST /api/lists" -> createList(exchange);
      case "DELETE /api/lists/{id}" -> deleteList(exchange, id);
      case "POST /api/lists/{id}/tasks" -> createTask(exchange, id);
      case "PATCH /api/tasks/{id}" -> updateTask(exchange, id);
      case "DELETE /api/tasks/{id}" -> deleteTask(exchange, id);
      default -> throw new ApiException(404, "Adresse inconnue : " + method + " " + path);
    }
  }

  private void createList(HttpExchange exchange) throws Exception {
    NewList request = readJson(exchange, NewList.class);
    String name = checkText(request.name(), "Le nom de la liste", MAX_NAME_LENGTH);
    // 201 Created : la liste a été créée ; on la renvoie avec son numéro.
    send(exchange, 201, repository.createList(name));
  }

  private void deleteList(HttpExchange exchange, long id) throws Exception {
    if (!repository.deleteList(id)) {
      throw new ApiException(404, "La liste " + id + " n'existe pas.");
    }
    // 204 No Content : c'est fait, et il n'y a rien à renvoyer.
    send(exchange, 204, null);
  }

  private void createTask(HttpExchange exchange, long listId) throws Exception {
    NewTask request = readJson(exchange, NewTask.class);
    String title = checkText(request.title(), "Le titre de la tâche", MAX_TITLE_LENGTH);
    Task task =
        repository
            .createTask(listId, title)
            .orElseThrow(() -> new ApiException(404, "La liste " + listId + " n'existe pas."));
    send(exchange, 201, task);
  }

  private void updateTask(HttpExchange exchange, long id) throws Exception {
    TaskChange request = readJson(exchange, TaskChange.class);
    if (request.done() == null) {
      throw new ApiException(400, "Le champ done (true ou false) est obligatoire.");
    }
    Task task =
        repository
            .updateTask(id, request.done())
            .orElseThrow(() -> new ApiException(404, "La tâche " + id + " n'existe pas."));
    send(exchange, 200, task);
  }

  private void deleteTask(HttpExchange exchange, long id) throws Exception {
    if (!repository.deleteTask(id)) {
      throw new ApiException(404, "La tâche " + id + " n'existe pas.");
    }
    send(exchange, 204, null);
  }

  /** Vérifie un texte saisi : obligatoire et pas trop long. Renvoie le texte sans espaces autour. */
  private static String checkText(String text, String label, int maxLength) throws ApiException {
    String value = text == null ? "" : text.strip();
    if (value.isEmpty()) {
      throw new ApiException(400, label + " est obligatoire.");
    }
    if (value.length() > maxLength) {
      throw new ApiException(400, label + " ne doit pas dépasser " + maxLength + " caractères.");
    }
    return value;
  }

  /** Lit le corps JSON de la requête et le convertit en objet Java du type demandé. */
  private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException, ApiException {
    byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw new ApiException(413, "La requête est trop volumineuse.");
    }
    T value = json.readValue(body, type);
    if (value == null) {
      throw new ApiException(400, "Le corps de la requête doit être un objet JSON.");
    }
    return value;
  }

  /**
   * Envoie la réponse : un code HTTP et, si body n'est pas null, un corps en JSON. Chaque réponse
   * est aussi affichée dans la console, pour suivre le dialogue avec le navigateur.
   */
  private void send(HttpExchange exchange, int status, Object body) throws IOException {
    System.out.printf(
        "%s  %-6s %-24s -> %d%n",
        LocalTime.now().format(TIME),
        exchange.getRequestMethod(),
        exchange.getRequestURI().getPath(),
        status);
    if (body == null) {
      exchange.sendResponseHeaders(status, -1); // -1 : réponse sans corps
      return;
    }
    byte[] bytes = json.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
