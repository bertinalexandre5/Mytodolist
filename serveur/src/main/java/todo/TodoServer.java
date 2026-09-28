package todo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serveur HTTP de la TodoList. Il reçoit les requêtes envoyées par l'application Angular, lit ou
 * modifie la base SQLite, puis répond en JSON.
 *
 * <p>Toutes les adresses commencent par {@code /api}. Les routes marquées 🔒 exigent l'en-tête
 * {@code Authorization: Bearer <jeton>}, obtenu en se connectant.
 *
 * <pre>
 * Routes de test, sans compte : une requête par action (étapes 08 et 09)
 * GET    /api/lists              toutes les listes, avec leurs tâches
 * POST   /api/lists              créer une liste         corps : {"name": "Courses"}
 * DELETE /api/lists/{id}         supprimer une liste (et ses tâches)
 * POST   /api/lists/{id}/tasks   ajouter une tâche       corps : {"title": "Pain"}
 * PATCH  /api/tasks/{id}         cocher ou décocher      corps : {"done": true}
 * DELETE /api/tasks/{id}         supprimer une tâche
 *
 * Synchronisation : toutes les listes d'un coup (étape 10 sans compte, étape 11 avec)
 * GET    /api/sync               sans jeton : les valeurs de départ ; avec : les listes du compte
 * PUT    /api/sync               sans jeton : vérifiées, pas enregistrées ; avec : enregistrées
 *
 * Comptes et sessions (étape 11)
 * POST   /api/accounts           créer un compte (et s'y connecter)  corps : {"username", "password"}
 * DELETE /api/accounts/me     🔒 supprimer son compte
 * POST   /api/sessions           se connecter                        corps : {"username", "password"}
 * DELETE /api/sessions/current 🔒 se déconnecter
 *
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

  // Limites de la synchronisation : de quoi faire de belles listes, pas de quoi remplir le disque.
  private static final int MAX_SYNC_BYTES = 256_000;
  private static final int MAX_LISTS = 100;
  private static final int MAX_TASKS = 1_000;

  // Règles des comptes.
  private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_PASSWORD_LENGTH = 128;
  private static final Duration SESSION_LIFETIME = Duration.ofDays(7);

  /** Un numéro dans une adresse, par exemple le 12 de /api/tasks/12. */
  private static final Pattern ID = Pattern.compile("/(\\d{1,18})(?=/|$)");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  // Forme du JSON attendu dans le corps des requêtes. Un champ absent vaut null.
  private record NewList(String name) {}

  private record NewTask(String title) {}

  private record TaskChange(Boolean done) {}

  private record Credentials(String username, String password) {}

  /** Réponse à une connexion réussie : le jeton à renvoyer ensuite, et l'identifiant. */
  private record Session(String token, String username) {}

  private final HttpServer http;
  private final TodoRepository repository;
  private final AccountRepository accounts;
  private final Config config;

  /** Convertit les objets Java en JSON et le JSON en objets Java. */
  private final JsonMapper json = JsonMapper.builder().build();

  private TodoServer(
      HttpServer http, TodoRepository repository, AccountRepository accounts, Config config) {
    this.http = http;
    this.repository = repository;
    this.accounts = accounts;
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
    // TodoRepository d'abord : c'est lui qui détecte une base neuve (fichier encore absent).
    TodoRepository repository = new TodoRepository(config.dbPath());
    if (config.demoData() && repository.isNew()) {
      repository.insertDemoData();
    }
    AccountRepository accounts = new AccountRepository(config.dbPath());
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), config.port());
    TodoServer server =
        new TodoServer(HttpServer.create(address, 0), repository, accounts, config);
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
    accounts.close();
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
      // Routes de test : une requête par action (étapes 08 et 09).
      case "GET /api/lists" -> send(exchange, 200, repository.findAllLists());
      case "POST /api/lists" -> createList(exchange);
      case "DELETE /api/lists/{id}" -> deleteList(exchange, id);
      case "POST /api/lists/{id}/tasks" -> createTask(exchange, id);
      case "PATCH /api/tasks/{id}" -> updateTask(exchange, id);
      case "DELETE /api/tasks/{id}" -> deleteTask(exchange, id);
      // Synchronisation (étapes 10 et 11).
      case "GET /api/sync" -> readSync(exchange);
      case "PUT /api/sync" -> writeSync(exchange);
      // Comptes et sessions (étape 11).
      case "POST /api/accounts" -> register(exchange);
      case "DELETE /api/accounts/me" -> deleteAccount(exchange);
      case "POST /api/sessions" -> login(exchange);
      case "DELETE /api/sessions/current" -> logout(exchange);
      default -> throw new ApiException(404, "Adresse inconnue : " + method + " " + path);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Routes de test : une requête par action, dans une base commune à tous (étapes 08 et 09)
  // ---------------------------------------------------------------------------------------------

  private void createList(HttpExchange exchange) throws Exception {
    NewList request = readJson(exchange, NewList.class, MAX_BODY_BYTES);
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
    NewTask request = readJson(exchange, NewTask.class, MAX_BODY_BYTES);
    String title = checkText(request.title(), "Le titre de la tâche", MAX_TITLE_LENGTH);
    Task task =
        repository
            .createTask(listId, title)
            .orElseThrow(() -> new ApiException(404, "La liste " + listId + " n'existe pas."));
    send(exchange, 201, task);
  }

  private void updateTask(HttpExchange exchange, long id) throws Exception {
    TaskChange request = readJson(exchange, TaskChange.class, MAX_BODY_BYTES);
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

  // ---------------------------------------------------------------------------------------------
  // Synchronisation : le navigateur lit ou envoie toutes ses listes d'un coup (étapes 10 et 11)
  // ---------------------------------------------------------------------------------------------

  /** GET /api/sync : les listes du compte connecté, ou les valeurs de départ en mode test. */
  private void readSync(HttpExchange exchange) throws Exception {
    Optional<Long> account = optionalAccount(exchange);
    if (account.isPresent()) {
      Optional<String> saved = accounts.findSavedLists(account.get());
      if (saved.isPresent()) {
        send(exchange, 200, json.readValue(saved.get(), TodoList[].class));
        return;
      }
    }
    // Mode test, ou compte qui n'a encore rien enregistré : les valeurs de départ.
    send(exchange, 200, startingLists());
  }

  /** PUT /api/sync : vérifie les listes reçues et les enregistre pour le compte connecté. */
  private void writeSync(HttpExchange exchange) throws Exception {
    Optional<Long> account = optionalAccount(exchange);
    List<TodoList> lists = checkLists(readJson(exchange, TodoList[].class, MAX_SYNC_BYTES));
    if (account.isPresent()) {
      accounts.saveLists(account.get(), json.writeValueAsString(lists));
    }
    send(exchange, 204, null);
    if (account.isEmpty()) {
      System.out.println("          (mode test : " + lists.size() + " liste(s) reçue(s), non enregistrée(s))");
    }
  }

  /** Les valeurs de départ, ou aucune liste si DEMO_DATA vaut false. */
  private List<TodoList> startingLists() {
    return config.demoData() ? StartingLists.LISTS : List.of();
  }

  /**
   * Vérifie les listes envoyées par le navigateur : noms et titres obligatoires et pas trop longs,
   * numéros positifs et uniques, quantités raisonnables. Renvoie les listes nettoyées (espaces
   * retirés autour des textes).
   */
  private static List<TodoList> checkLists(TodoList[] lists) throws ApiException {
    if (lists.length > MAX_LISTS) {
      throw new ApiException(400, "Pas plus de " + MAX_LISTS + " listes.");
    }
    Set<Long> listIds = new HashSet<>();
    Set<Long> taskIds = new HashSet<>();
    List<TodoList> checked = new ArrayList<>();
    for (TodoList list : lists) {
      if (list == null || list.tasks() == null) {
        throw new ApiException(400, "Chaque liste doit avoir un numéro, un nom et des tâches.");
      }
      // Set.add renvoie false si le numéro y était déjà : deux listes ont alors le même.
      if (list.id() <= 0 || !listIds.add(list.id())) {
        throw new ApiException(400, "Numéro de liste invalide ou en double : " + list.id());
      }
      List<Task> tasks = new ArrayList<>();
      for (Task task : list.tasks()) {
        if (task == null || task.id() <= 0 || !taskIds.add(task.id())) {
          throw new ApiException(400, "Tâche sans numéro, ou numéro de tâche en double.");
        }
        String title = checkText(task.title(), "Le titre de la tâche", MAX_TITLE_LENGTH);
        tasks.add(new Task(task.id(), title, task.done()));
      }
      String name = checkText(list.name(), "Le nom de la liste", MAX_NAME_LENGTH);
      checked.add(new TodoList(list.id(), name, tasks));
    }
    if (taskIds.size() > MAX_TASKS) {
      throw new ApiException(400, "Pas plus de " + MAX_TASKS + " tâches en tout.");
    }
    return checked;
  }

  // ---------------------------------------------------------------------------------------------
  // Comptes et sessions (étape 11)
  // ---------------------------------------------------------------------------------------------

  /** POST /api/accounts : crée un compte et ouvre aussitôt une session. */
  private void register(HttpExchange exchange) throws Exception {
    Credentials request = readJson(exchange, Credentials.class, MAX_BODY_BYTES);
    String username = request.username() == null ? "" : request.username().strip();
    if (!USERNAME.matcher(username).matches()) {
      throw new ApiException(
          400,
          "L'identifiant doit faire de 3 à 30 caractères : lettres sans accent, chiffres,"
              + " point, tiret ou tiret bas.");
    }
    String password = request.password() == null ? "" : request.password();
    if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
      throw new ApiException(
          400,
          "Le mot de passe doit faire de "
              + MIN_PASSWORD_LENGTH
              + " à "
              + MAX_PASSWORD_LENGTH
              + " caractères.");
    }
    byte[] salt = Security.newSalt();
    long accountId =
        accounts
            .createAccount(username, salt, Security.hashPassword(password, salt))
            .orElseThrow(() -> new ApiException(409, "L'identifiant " + username + " est déjà pris."));
    send(exchange, 201, openSession(accountId, username));
  }

  /** POST /api/sessions : vérifie l'identifiant et le mot de passe, puis ouvre une session. */
  private void login(HttpExchange exchange) throws Exception {
    Credentials request = readJson(exchange, Credentials.class, MAX_BODY_BYTES);
    String username = request.username() == null ? "" : request.username().strip();
    String password = request.password() == null ? "" : request.password();
    Optional<AccountRepository.Account> account = accounts.findAccount(username);
    boolean valid =
        account.isPresent()
            && password.length() <= MAX_PASSWORD_LENGTH
            && Security.checkPassword(password, account.get().salt(), account.get().passwordHash());
    if (!valid) {
      // Même message que l'identifiant soit inconnu ou le mot de passe faux : on ne dit pas lequel.
      throw new ApiException(401, "Identifiant ou mot de passe incorrect.");
    }
    send(exchange, 200, openSession(account.get().id(), account.get().username()));
  }

  /** DELETE /api/sessions/current 🔒 : le jeton de la requête ne sera plus accepté. */
  private void logout(HttpExchange exchange) throws Exception {
    requireAccount(exchange);
    accounts.deleteSession(Security.sha256(bearerToken(exchange).orElseThrow()));
    send(exchange, 204, null);
  }

  /** DELETE /api/accounts/me 🔒 : supprime le compte connecté, ses sessions et ses listes. */
  private void deleteAccount(HttpExchange exchange) throws Exception {
    accounts.deleteAccount(requireAccount(exchange));
    send(exchange, 204, null);
  }

  /** Crée une session pour ce compte et renvoie le jeton, à ne montrer qu'à son propriétaire. */
  private Session openSession(long accountId, String username) throws Exception {
    String token = Security.newToken();
    accounts.deleteExpiredSessions(now());
    accounts.createSession(
        Security.sha256(token), accountId, now() + SESSION_LIFETIME.toSeconds());
    return new Session(token, username);
  }

  /** Le compte de la requête, obligatoire : sinon, erreur 401 (non authentifié). */
  private long requireAccount(HttpExchange exchange) throws Exception {
    String token =
        bearerToken(exchange).orElseThrow(() -> new ApiException(401, "Connexion requise."));
    return accounts
        .findSessionAccount(Security.sha256(token), now())
        .orElseThrow(() -> new ApiException(401, "Session inconnue ou expirée : reconnectez-vous."));
  }

  /**
   * Le compte de la requête s'il y a un jeton, ou « rien » sans jeton (mode test). Un jeton
   * invalide ou expiré donne une erreur 401 : le navigateur se croit connecté, il faut le prévenir.
   */
  private Optional<Long> optionalAccount(HttpExchange exchange) throws Exception {
    if (bearerToken(exchange).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(requireAccount(exchange));
  }

  /** Le jeton de l'en-tête « Authorization: Bearer <jeton> », s'il y en a un. */
  private static Optional<String> bearerToken(HttpExchange exchange) throws ApiException {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null) {
      return Optional.empty();
    }
    if (!header.startsWith("Bearer ")) {
      throw new ApiException(401, "En-tête Authorization invalide.");
    }
    return Optional.of(header.substring("Bearer ".length()));
  }

  /** L'heure actuelle, en secondes depuis le 1er janvier 1970. */
  private static long now() {
    return Instant.now().getEpochSecond();
  }

  // ---------------------------------------------------------------------------------------------
  // Outils communs
  // ---------------------------------------------------------------------------------------------

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

  /**
   * Lit le corps JSON de la requête (au plus maxBytes octets) et le convertit en objet Java du
   * type demandé.
   */
  private <T> T readJson(HttpExchange exchange, Class<T> type, int maxBytes)
      throws IOException, ApiException {
    byte[] body = exchange.getRequestBody().readNBytes(maxBytes + 1);
    if (body.length > maxBytes) {
      throw new ApiException(413, "La requête est trop volumineuse.");
    }
    T value = json.readValue(body, type);
    if (value == null) {
      throw new ApiException(400, "Le corps de la requête ne doit pas être vide.");
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
