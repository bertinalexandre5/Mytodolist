# Serveur TodoList

Petit serveur Java livré avec le cours. Il ne fait pas partie du pas-à-pas : il est fourni prêt à l'emploi pour que l'application Angular puisse dialoguer avec un vrai serveur, à partir de l'[étape 08](../08-dialoguer-avec-le-serveur/README.md).

Il enregistre les listes et leurs tâches dans une base **SQLite**, c'est-à-dire un simple fichier (`todo.db`), et les expose sous forme d'une **API** HTTP qui parle JSON.

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

Au premier lancement, la base `todo.db` est créée dans ce dossier, avec trois listes d'exemple. Pour repartir de zéro : arrêtez le serveur, supprimez `todo.db` et relancez.

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

| Réglage     | Rôle                                                                          | Valeur par défaut |
| ----------- | ----------------------------------------------------------------------------- | ----------------- |
| `PORT`      | Port d'écoute du serveur.                                                     | `8080`            |
| `DB_PATH`   | Fichier de la base SQLite, relatif au dossier `serveur`.                      | `todo.db`         |
| `DEMO_DATA` | `true` : crée des listes d'exemple dans une base neuve ; `false` : base vide. | `true`            |
| `DELAY_MS`  | Pause avant chaque réponse, en millisecondes, pour simuler un réseau lent.    | `0`               |

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

Toutes les adresses commencent par `/api`. Les corps de requête et de réponse sont en JSON.

| Requête                      | Corps envoyé          | Réponses                                                        |
| ---------------------------- | --------------------- | --------------------------------------------------------------- |
| `GET /api/lists`             |                       | `200` : toutes les listes, avec leurs tâches                    |
| `POST /api/lists`            | `{"name": "Courses"}` | `201` : la liste créée · `400` : nom vide ou trop long          |
| `DELETE /api/lists/{id}`     |                       | `204` : supprimée, avec ses tâches · `404`                      |
| `POST /api/lists/{id}/tasks` | `{"title": "Pain"}`   | `201` : la tâche créée · `400` · `404` : liste inconnue         |
| `PATCH /api/tasks/{id}`      | `{"done": true}`      | `200` : la tâche modifiée · `400` · `404`                       |
| `DELETE /api/tasks/{id}`     |                       | `204` : supprimée · `404`                                       |
| `GET /api/health`            |                       | `200` : `{"status": "ok"}`, pour vérifier que le serveur répond |

Une liste, telle que renvoyée par `GET /api/lists` :

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

En cas d'erreur, la réponse explique le problème : `{"error": "Le nom de la liste est obligatoire."}`.

Règles : le nom d'une liste fait de 1 à 100 caractères, le titre d'une tâche de 1 à 200 (les espaces au début et à la fin sont retirés). Une nouvelle tâche n'est pas réalisée. Les numéros (`id`) sont attribués par la base et ne sont jamais réutilisés.

Essai rapide depuis un terminal, pendant que le serveur tourne :

```sh
curl localhost:8080/api/lists
curl -X POST localhost:8080/api/lists -H "Content-Type: application/json" -d '{"name": "Week-end"}'
curl -X PATCH localhost:8080/api/tasks/2 -H "Content-Type: application/json" -d '{"done": true}'
```

Sous Windows PowerShell, `curl` n'est pas le vrai curl : utilisez `curl.exe`, ou `Invoke-RestMethod` :

```powershell
Invoke-RestMethod http://localhost:8080/api/lists
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/lists -ContentType 'application/json' -Body '{"name": "Week-end"}'
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
| `TodoRepository.java`        | Toutes les requêtes SQL vers la base SQLite.                                             |
| `Config.java`                | La lecture des réglages : valeurs par défaut, fichier `.env`, variables d'environnement. |
| `TodoList.java`, `Task.java` | La forme des données renvoyées en JSON.                                                  |
| `ApiException.java`          | Une erreur à renvoyer au navigateur, avec son code HTTP.                                 |

Il n'utilise que le serveur HTTP fourni avec le JDK, le pilote JDBC de SQLite et la bibliothèque Jackson pour le JSON (voir [pom.xml](pom.xml)).

## Limites

Ce serveur est fait pour apprendre, sur son propre poste :

- il n'écoute que sur `localhost` : les autres machines du réseau ne peuvent pas l'atteindre ;
- il n'y a ni comptes ni mots de passe : quiconque accède au port peut tout lire et tout modifier ;
- il traite les requêtes une par une, ce qui suffit largement pour un utilisateur.
