import { Injectable, signal } from '@angular/core';
import { Task, TodoList } from './todo';

@Injectable({ providedIn: 'root' })
export class TodoStore {
  /** Les listes, dans un signal modifiable. « private » : seul ce service peut y toucher. */
  private readonly writableLists = signal<TodoList[]>([
    {
      id: 1,
      name: 'Courses',
      tasks: [
        { id: 1, title: 'Pain', done: true },
        { id: 2, title: 'Lait', done: false },
        { id: 3, title: 'Pommes', done: false },
      ],
    },
    {
      id: 2,
      name: 'Préparer les vacances',
      tasks: [
        { id: 4, title: 'Réserver le train', done: true },
        { id: 5, title: 'Faire la valise', done: false },
      ],
    },
    {
      id: 3,
      name: 'Découvrir Angular',
      tasks: [
        { id: 6, title: 'Installer Node.js', done: true },
        { id: 7, title: 'Créer le projet', done: true },
      ],
    },
    {
      id: 4,
      name: 'Idées de cadeaux',
      tasks: [],
    },
  ]);

  /** Les mêmes listes, en lecture seule, pour les composants. */
  readonly lists = this.writableLists.asReadonly();

  /** Numéro à donner à la prochaine liste ou tâche créée. */
  private nextId = 100;

  /** Crée une liste vide portant ce nom. */
  createList(name: string): void {
    const list: TodoList = { id: this.nextId++, name: name, tasks: [] };
    this.writableLists.update((lists) => [...lists, list]);
  }

  /** Supprime une liste et toutes ses tâches. */
  deleteList(list: TodoList): void {
    this.writableLists.update((lists) => lists.filter((current) => current.id !== list.id));
  }

  /** Ajoute à une liste une tâche, non réalisée. */
  addTask(list: TodoList, title: string): void {
    const task: Task = { id: this.nextId++, title: title, done: false };
    this.updateTasks(list, (tasks) => [...tasks, task]);
  }

  /** Coche la tâche si elle ne l'était pas, et inversement. */
  toggleTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) =>
      tasks.map((current) =>
        current.id === task.id ? { ...current, done: !current.done } : current,
      ),
    );
  }

  /** Supprime une tâche de sa liste. */
  deleteTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) => tasks.filter((current) => current.id !== task.id));
  }

  /** Modifie les tâches d'une seule liste. */
  private updateTasks(list: TodoList, change: (tasks: Task[]) => Task[]): void {
    this.writableLists.update((lists) =>
      lists.map((current) =>
        current.id === list.id ? { ...current, tasks: change(current.tasks) } : current,
      ),
    );
  }
}
