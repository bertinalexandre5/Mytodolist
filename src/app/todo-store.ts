import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Task, TodoList } from './todo';

@Injectable({ providedIn: 'root' })
export class TodoStore {
  /** Le service d'Angular qui envoie des requêtes HTTP. */
  private readonly http = inject(HttpClient);

  /** Les listes. Au départ vide : elles arrivent quand le serveur a répondu. */
  private readonly writableLists = signal<TodoList[]>([]);
  readonly lists = this.writableLists.asReadonly();

  /** Vrai pendant le chargement des listes : App affiche alors « Chargement… ». */
  private readonly writableLoading = signal(false);
  readonly loading = this.writableLoading.asReadonly();

  /** Le message d'erreur à afficher, ou null quand tout va bien. */
  private readonly writableError = signal<string | null>(null);
  readonly error = this.writableError.asReadonly();

  /** S'exécute à la création du service : on charge les listes. */
  constructor() {
    this.load();
  }

  /** Demande au serveur toutes les listes, avec leurs tâches. */
  load(): void {
    this.writableLoading.set(true);
    this.writableError.set(null);
    this.http.get<TodoList[]>('/api/lists').subscribe({
      next: (lists) => {
        this.writableLists.set(lists);
        this.writableLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.writableLoading.set(false);
      },
    });
  }

  /** Crée une liste vide portant ce nom. */
  createList(name: string): void {
    this.http.post<TodoList>('/api/lists', { name: name }).subscribe({
      next: (list) => this.writableLists.update((lists) => [...lists, list]),
      error: (error: HttpErrorResponse) => this.showError(error),
    });
  }

  /** Supprime une liste et toutes ses tâches. */
  deleteList(list: TodoList): void {
    this.http.delete(`/api/lists/${list.id}`).subscribe({
      next: () =>
        this.writableLists.update((lists) => lists.filter((current) => current.id !== list.id)),
      error: (error: HttpErrorResponse) => this.showError(error),
    });
  }

  /** Ajoute à une liste une tâche, non réalisée. */
  addTask(list: TodoList, title: string): void {
    this.http.post<Task>(`/api/lists/${list.id}/tasks`, { title: title }).subscribe({
      next: (task) => this.updateTasks(list, (tasks) => [...tasks, task]),
      error: (error: HttpErrorResponse) => this.showError(error),
    });
  }

  /** Coche la tâche si elle ne l'était pas, et inversement. */
  toggleTask(list: TodoList, task: Task): void {
    this.http.patch<Task>(`/api/tasks/${task.id}`, { done: !task.done }).subscribe({
      next: (changed) =>
        this.updateTasks(list, (tasks) =>
          tasks.map((current) => (current.id === changed.id ? changed : current)),
        ),
      error: (error: HttpErrorResponse) => this.showError(error),
    });
  }

  /** Supprime une tâche de sa liste. */
  deleteTask(list: TodoList, task: Task): void {
    this.http.delete(`/api/tasks/${task.id}`).subscribe({
      next: () =>
        this.updateTasks(list, (tasks) => tasks.filter((current) => current.id !== task.id)),
      error: (error: HttpErrorResponse) => this.showError(error),
    });
  }

  /** Fait disparaître le message d'erreur (bouton ✕ du bandeau). */
  clearError(): void {
    this.writableError.set(null);
  }

  /** Transforme une erreur HTTP en message compréhensible, rangé dans le signal error. */
  private showError(error: HttpErrorResponse): void {
    const serverMessage = error.error?.error;
    if (typeof serverMessage === 'string') {
      this.writableError.set(serverMessage);
    } else {
      this.writableError.set('Impossible de joindre le serveur. Est-il bien lancé ?');
    }
    console.error(error);
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
