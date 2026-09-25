import { Component, signal } from '@angular/core';
import {Task, TodoList} from './todo';
import {ListCard} from './list-card/list-card';

@Component({
  selector: 'app-root',
  imports: [ListCard],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = 'Mes listes de tâches';

protected readonly lists = signal<TodoList[]>([
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

  /** Coche la tâche si elle ne l'était pas, et inversement. */
  protected toggleTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) =>
      tasks.map((current) =>
        current.id === task.id ? { ...current, done: !current.done } : current,
      ),
    );
  }

  /** Supprime une tâche de sa liste. */
  protected deleteTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) => tasks.filter((current) => current.id !== task.id));
  }

  /** Supprime une liste et toutes ses tâches, après confirmation. */
  protected deleteList(list: TodoList): void {
    if (!confirm(`Supprimer la liste « ${list.name} » et toutes ses tâches ?`)) {
      return;
    }
    this.lists.update((lists) => lists.filter((current) => current.id !== list.id));
  }

  /** Modifie les tâches d'une seule liste. */
  private updateTasks(list: TodoList, change: (tasks: Task[]) => Task[]): void {
    this.lists.update((lists) =>
      lists.map((current) =>
        current.id === list.id ? { ...current, tasks: change(current.tasks) } : current,
      ),
    );
  }
}
