/** Une tâche à réaliser. */
export interface Task {
  id: number; // numéro unique
  title: string; // ce qu'il y a à faire
  done: boolean; // true quand la tâche est réalisée
}

/** Une liste de tâches, avec son nom. */
export interface TodoList {
  id: number;
  name: string;
  tasks: Task[]; // « un tableau de Task »
}
