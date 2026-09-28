# Serveur TodoList

Petit serveur Java livré avec le cours. Il ne fait pas partie du pas-à-pas : il est fourni prêt à l'emploi pour que l'application Angular puisse dialoguer avec un vrai serveur, à partir de l'[étape 08](../08-dialoguer-avec-le-serveur/README.md).

Il enregistre les données dans une base **SQLite**, c'est-à-dire un simple fichier (`todo.db`), et les expose sous forme d'une **API** HTTP qui parle JSON. Il propose deux familles de routes :

- des **routes de test**, sans compte, pour les étapes 08, 09 et 10 ;
- des **comptes** (création, connexion, déconnexion, suppression) et une **synchronisation authentifiée**, pour les étapes 11 et 12.

## Prérequis

- Un **JDK 21** ou plus récent, par exemple [Eclipse Temurin](https://adoptium.net/). Vérifiez avec `java -version`.
- Rien d'autre : le script `mvnw` (le « Maven Wrapper ») télécharge Maven et les bibliothèques au premier lancement. Il faut donc une connexion Internet la première fois.

Sous Windows, `java` doit être dans le `PATH`, ou la variable `JAVA_HOME` doit pointer vers le JDK ; l'installeur Temurin propose de s'en charger.

## Lancer le serveur

Depuis ce dossier `serveur` :

```sh
./mvnw compile exec:java        # macOS, Linux, Git Bash
```

```powershell
.\mvnw.cmd compile exec:java    # Windows (PowerShell ou invite de commandes)
```

Le premier lancement prend un peu de temps (téléchargements). Le serveur affiche ensuite :

```text
Serveur TodoList : http://localhost:8080/api/lists
Base de données  : …/serveur/todo.db
Ctrl+C pour arrêter. Requêtes reçues :
```

puis une ligne par requête reçue, avec son code de réponse. `Ctrl+C` l'arrête.

Au premier lancement, la base `todo.db` est créée dans ce dossier, avec trois listes d'exemple pour les routes de test. Pour repartir de zéro (listes de test **et** comptes) : arrêtez le serveur, supprimez `todo.db` et relancez.

Si `./mvnw` répond `Permission denied` (macOS, Linux), rendez le script exécutable une fois pour toutes avec `chmod +x mvnw`, ou lancez-le avec `sh mvnw compile exec:java`.

## Les réglages : le fichier `.env`

Chaque réglage a une valeur par défaut. Pour en changer, créez un fichier `.env` dans ce dossier, en copiant le modèle [.env.example](.env.example) :

```sh
cp .env.example .env            # macOS, Linux, Git Bash
```

```powershell
Copy-Item .env.example .env     # Windows PowerShell
```

puis modifiez les lignes voulues, au format `NOM=valeur`, et relancez le serveur.

| Réglage     | Rôle                                                                                                       | Valeur par défaut |
| ----------- | ---------------------------------------------------------------------------------------------------------- | ----------------- |
| `PORT`      | Port d'écoute du serveur.                                                                                  | `8080`            |
| `DB_PATH`   | Fichier de la base SQLite, relatif au dossier `serveur`.                                                   | `todo.db`         |
| `DEMO_DATA` | `true` : listes d'exemple dans une base neuve, et comme valeurs de départ de `/api/sync` ; `false` : rien. | `true`            |
| `DELAY_MS`  | Pause avant chaque réponse, en millisecondes, pour simuler un réseau lent.                                 | `0`               |

Par exemple, ce fichier `.env` ralentit les réponses et utilise une autre base :

```sh
DELAY_MS=1500
DB_PATH=essai.db
```

Ordre de priorité, du plus faible au plus fort : la valeur par défaut, puis le fichier `.env`, puis une **variable d'environnement** du même nom. Cette dernière est pratique pour un essai ponctuel, sans toucher au fichier :

```sh
PORT=9090 ./mvnw compile exec:java                 # macOS, Linux, Git Bash
```

```powershell
$env:PORT = 9090; .\mvnw.cmd compile exec:java     # PowerShell (valable jusqu'à la fermeture du terminal)
```

Le fichier `.env` est ignoré par Git (voir `.gitignore` à la racine du cours) : chacun garde ses propres réglages.

**Si vous changez `PORT`**, changez aussi `target` dans le fichier `proxy.conf.json` de l'étape Angular que vous lancez, puis relancez `npm start`.

## L'API

Toutes les adresses commencent par `/api`. Les corps de requête et de réponse sont en JSON. En cas d'erreur, la réponse explique le problème : `{"error": "Le nom de la liste est obligatoire."}`.

Une liste, telle que le serveur l'envoie et la reçoit :

```json
{
  "id": 1,
  "name": "Courses",
  "tasks": [
    { "id": 1, "title": "Pain", "done": true },
    { "id": 2, "title": "Lait", "done": false }
  ]
}
```

Règles communes : le nom d'une liste fait de 1 à 100 caractères, le titre d'une tâche de 1 à 200 ; les espaces au début et à la fin sont retirés.

### Routes de test, une requête par action (étapes 08 et 09)

Sans compte. Les listes sont enregistrées dans une base **commune à tous**.

| Requête                      | Corps envoyé          | Réponses                                                |
| ---------------------------- | --------------------- | ------------------------------------------------------- |
| `GET /api/lists`             |                       | `200` : toutes les listes, avec leurs tâches            |
| `POST /api/lists`            | `{"name": "Courses"}` | `201` : la liste créée · `400` : nom vide ou trop long  |
| `DELETE /api/lists/{id}`     |                       | `204` : supprimée, avec ses tâches · `404`              |
| `POST /api/lists/{id}/tasks` | `{"title": "Pain"}`   | `201` : la tâche créée · `400` · `404` : liste inconnue |
| `PATCH /api/tasks/{id}`      | `{"done": true}`      | `200` : la tâche modifiée · `400` · `404`               |
| `DELETE /api/tasks/{id}`     |                       | `204` : supprimée · `404`                               |

Ici, les numéros (`id`) sont attribués par la base et ne sont jamais réutilisés. Une nouvelle tâche n'est pas réalisée.

### Synchronisation, toutes les listes d'un coup (étapes 10, 11 et 12)

La même adresse sert avec ou sans compte. Tout dépend de l'en-tête `Authorization: Bearer <jeton>` :

| Requête         | Sans jeton : mode test (étape 10)                 | Avec un jeton valable (étapes 11 et 12)                                   |
| --------------- | ------------------------------------------------- | ------------------------------------------------------------------------- |
| `GET /api/sync` | `200` : les **valeurs de départ**                 | `200` : les listes du compte (valeurs de départ s'il n'a rien enregistré) |
| `PUT /api/sync` | `204` : listes vérifiées, mais **pas conservées** | `204` : listes enregistrées dans le compte, à la place des précédentes    |

Le corps de `PUT /api/sync` est le tableau de **toutes** les listes. Ici, c'est le navigateur qui choisit les numéros ; ils doivent être positifs et uniques. Au plus 100 listes, 1 000 tâches et 256 Ko. Avec un jeton invalide ou expiré, la réponse est `401`.

### Comptes et sessions (étapes 11 et 12)

Les routes marquées 🔒 exigent l'en-tête `Authorization: Bearer <jeton>`.

| Requête                           | Corps envoyé                             | Réponses                                                                                     |
| --------------------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------- |
| `POST /api/accounts`              | `{"username": "alice", "password": "…"}` | `201` + `{"token", "username"}` : compte créé et connecté · `400` · `409` : identifiant pris |
| `POST /api/sessions`              | `{"username": "alice", "password": "…"}` | `200` + `{"token", "username"}` · `401` : identifiant ou mot de passe incorrect              |
| `DELETE /api/sessions/current` 🔒 |                                          | `204` : déconnecté, le jeton n'est plus accepté                                              |
| `DELETE /api/accounts/me` 🔒      |                                          | `204` : compte supprimé, avec ses sessions et ses listes                                     |

Règles : l'identifiant fait de 3 à 30 caractères (lettres sans accent, chiffres, `.`, `-`, `_`), sans distinction de majuscules ; le mot de passe fait de 8 à 128 caractères. Une session dure 7 jours.

### Divers

| Requête           | Réponse                                                         |
| ----------------- | --------------------------------------------------------------- |
| `GET /api/health` | `200` : `{"status": "ok"}`, pour vérifier que le serveur répond |

### Essais depuis un terminal

Pendant que le serveur tourne :

```sh
# Routes de test
curl localhost:8080/api/lists
curl -X POST localhost:8080/api/lists -H "Content-Type: application/json" -d '{"name": "Week-end"}'

# Comptes et synchronisation
curl -X POST localhost:8080/api/accounts -H "Content-Type: application/json" -d '{"username": "alice", "password": "motdepasse"}'
curl localhost:8080/api/sync -H "Authorization: Bearer <le jeton reçu>"
```

Sous Windows PowerShell, `curl` n'est pas le vrai curl : utilisez `curl.exe`, ou `Invoke-RestMethod` :

```powershell
Invoke-RestMethod http://localhost:8080/api/lists
$session = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/accounts -ContentType 'application/json' -Body '{"username": "alice", "password": "motdepasse"}'
Invoke-RestMethod http://localhost:8080/api/sync -Headers @{ Authorization = "Bearer $($session.token)" }
```

## Tests

```sh
./mvnw test          # ou .\mvnw.cmd test sous Windows
```

Les tests démarrent un vrai serveur, sur un port libre et avec une base en mémoire, puis lui envoient des requêtes.

## Organisation du code

Tout est dans [src/main/java/todo/](src/main/java/todo/), abondamment commenté :

| Fichier                      | Rôle                                                                                     |
| ---------------------------- | ---------------------------------------------------------------------------------------- |
| `TodoServer.java`            | Le serveur HTTP : reconnaît chaque adresse, vérifie les données, répond en JSON.         |
| `TodoRepository.java`        | Les requêtes SQL des routes de test (tables `todo_lists` et `tasks`).                    |
| `AccountRepository.java`     | Les requêtes SQL des comptes, des sessions et des listes enregistrées.                   |
| `Security.java`              | Les empreintes de mots de passe et les jetons de session.                                |
| `StartingLists.java`         | Les valeurs de départ : les listes d'exemple.                                            |
| `Config.java`                | La lecture des réglages : valeurs par défaut, fichier `.env`, variables d'environnement. |
| `TodoList.java`, `Task.java` | La forme des données échangées en JSON.                                                  |
| `ApiException.java`          | Une erreur à renvoyer au navigateur, avec son code HTTP.                                 |

Il n'utilise que le serveur HTTP fourni avec le JDK, le pilote JDBC de SQLite et la bibliothèque Jackson pour le JSON (voir [pom.xml](pom.xml)).

## Sécurité et limites

Ce que le serveur fait correctement :

- les mots de passe ne sont jamais enregistrés tels quels, seulement leur empreinte PBKDF2-HMAC-SHA256 (600 000 itérations, avec un sel aléatoire propre à chaque compte) ;
- les jetons de session sont aléatoires ; la base n'en garde que l'empreinte SHA-256 ;
- une connexion refusée ne dit pas si c'est l'identifiant ou le mot de passe qui est faux.

Ce serveur reste fait pour apprendre, sur son propre poste :

- il n'écoute que sur `localhost` : les autres machines du réseau ne peuvent pas l'atteindre ;
- il parle HTTP sans chiffrement ; sur Internet, il faudrait HTTPS, et limiter le nombre d'essais de connexion ;
- les routes de test ne demandent aucun compte : quiconque accède au port peut lire et modifier leurs listes ;
- il traite les requêtes une par une, ce qui suffit largement pour un utilisateur.
