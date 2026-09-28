package todo;

import java.util.List;

/**
 * Les « valeurs de départ » : quelques listes d'exemple.
 *
 * <p>Elles servent deux fois :
 *
 * <ul>
 *   <li>au premier lancement, pour remplir la base des routes de test /api/lists (étapes 08-09) ;
 *   <li>comme réponse de GET /api/sync sans compte (étape 10), ou pour un compte qui n'a encore
 *       rien enregistré (étape 11).
 * </ul>
 */
final class StartingLists {

  static final List<TodoList> LISTS =
      List.of(
          new TodoList(
              1,
              "Courses",
              List.of(new Task(1, "Pain", true), new Task(2, "Lait", false), new Task(3, "Pommes", false))),
          new TodoList(
              2,
              "Préparer les vacances",
              List.of(new Task(4, "Réserver le train", true), new Task(5, "Faire la valise", false))),
          // Toutes ses tâches sont réalisées : son nom s'affichera barré.
          new TodoList(
              3,
              "Découvrir Angular",
              List.of(new Task(6, "Installer Node.js", true), new Task(7, "Créer le projet", true))));

  private StartingLists() {}
}
