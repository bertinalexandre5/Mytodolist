package todo;

/**
 * Une tâche, telle qu'elle est envoyée au navigateur.
 *
 * <p>Un « record » Java est une petite classe qui ne fait que porter des données. Jackson le
 * transforme en JSON avec un champ par composant, par exemple :
 * {@code {"id": 3, "title": "Acheter du pain", "done": false}}.
 *
 * @param id numéro unique, attribué par la base de données
 * @param title ce qu'il y a à faire
 * @param done {@code true} quand la tâche est réalisée
 */
public record Task(long id, String title, boolean done) {}
