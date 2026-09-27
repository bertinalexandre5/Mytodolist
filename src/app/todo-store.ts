import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Task, TodoList } from './todo';

@Injectable({ providedIn: 'root' })
export class TodoStore {
  /** Le service d'Angular qui envoie des requêtes HTTP. */
  private readonly http = inject(HttpClient);

  /** Les listes. Au départ vide : elles arrivent quand le serveur a répondu. */
  private readonly writableLists = signal<TodoList[]>([]);

  /** Les mêmes listes, en lecture seule, pour les composants. */
  readonly lists = this.writableLists.asReadonly();

  /** S'exécute à la création du service : on charge les listes. */
  constructor() {
    this.load();
  }

  /** Demande au serveur toutes les listes, avec leurs tâches. */
  load(): void {
    this.http.get<TodoList[]>('/api/lists').subscribe((lists) => {
      this.writableLists.set(lists);
    });
  }

  /** Crée une liste vide portant ce nom. */
  createList(name: string): void {
    this.http.post<TodoList>('/api/lists', { name: name }).subscribe((list) => {
      this.writableLists.update((lists) => [...lists, list]);
    });
  }

  /** Supprime une liste et toutes ses tâches. */
  deleteList(list: TodoList): void {
    this.http.delete(`/api/lists/${list.id}`).subscribe(() => {
      this.writableLists.update((lists) => lists.filter((current) => current.id !== list.id));
    });
  }

  /** Ajoute à une liste une tâche, non réalisée. */
  addTask(list: TodoList, title: string): void {
    this.http.post<Task>(`/api/lists/${list.id}/tasks`, { title: title }).subscribe((task) => {
      this.updateTasks(list, (tasks) => [...tasks, task]);
    });
  }

  /** Coche la tâche si elle ne l'était pas, et inversement. */
  toggleTask(list: TodoList, task: Task): void {
    this.http.patch<Task>(`/api/tasks/${task.id}`, { done: !task.done }).subscribe((changed) => {
      this.updateTasks(list, (tasks) =>
        tasks.map((current) => (current.id === changed.id ? changed : current)),
      );
    });
  }

  /** Supprime une tâche de sa liste. */
  deleteTask(list: TodoList, task: Task): void {
    this.http.delete(`/api/tasks/${task.id}`).subscribe(() => {
      this.updateTasks(list, (tasks) => tasks.filter((current) => current.id !== task.id));
    });
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
