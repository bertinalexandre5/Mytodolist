package todo;

import java.util.List;

/**
 * Une liste de tâches, avec ses tâches, telle qu'elle est envoyée au navigateur.
 *
 * <p>En JSON : {@code {"id": 1, "name": "Courses", "tasks": [ ...les tâches... ]}}.
 *
 * @param id numéro unique, attribué par la base de données
 * @param name nom de la liste, choisi à sa création
 * @param tasks tâches de la liste, dans l'ordre où elles ont été ajoutées
 */
public record TodoList(long id, String name, List<Task> tasks) {}
