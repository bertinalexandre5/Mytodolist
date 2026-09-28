import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Task, TodoList } from './todo';
import {AuthStore} from './auth-store';
import {errorMessage} from './error-message';

@Injectable({ providedIn: 'root' })
export class TodoStore {
  private readonly http = inject(HttpClient);

  /** Le service du compte : utile seulement pour oublier une session refusée par le serveur. */
  private readonly auth = inject(AuthStore);

  /** Les listes. Au départ, le tableau est vide : elles arrivent quand le serveur a répondu. */
  private readonly writableLists = signal<TodoList[]>([]);
  readonly lists = this.writableLists.asReadonly();

  /** Vrai pendant le chargement des listes : App affiche alors « Chargement… ». */
  private readonly writableLoading = signal(false);
  readonly loading = this.writableLoading.asReadonly();

  /** Vrai pendant l'envoi des listes au serveur : App affiche alors « Envoi… ». */
  private readonly writableSaving = signal(false);
  readonly saving = this.writableSaving.asReadonly();

  /** Le message d'erreur à afficher, ou null quand tout va bien. */
  private readonly writableError = signal<string | null>(null);
  readonly error = this.writableError.asReadonly();

  /** Vrai une fois les listes reçues du serveur. Avant, surtout ne rien envoyer (voir sync). */
  private loaded = false;

  /** Vrai pendant qu'une requête d'envoi est en route. */
  private sending = false;

  /** Vrai si les listes ont encore changé pendant cet envoi : il faudra les renvoyer. */
  private changedWhileSending = false;

  constructor() {
    this.load();
  }

  /** Demande au serveur toutes les listes : GET /api/sync. */
  load(): void {
    this.loaded = false;
    this.changedWhileSending = false;
    this.writableLoading.set(true);
    this.writableError.set(null);
    this.http.get<TodoList[]>('/api/sync').subscribe({
      next: (lists) => {
        this.writableLists.set(lists);
        this.loaded = true;
        this.writableLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.writableLoading.set(false);
      },
    });
  }

  /** Réessaie ce qui a échoué : le chargement si les listes n'ont jamais été reçues, sinon l'envoi. */
  retry(): void {
    if (this.loaded) {
      this.sync();
    } else {
      this.load();
    }
  }

  /** Crée une liste vide portant ce nom. */
  createList(name: string): void {
    const list: TodoList = { id: this.newId(), name: name, tasks: [] };
    this.writableLists.update((lists) => [...lists, list]);
    this.sync();
  }

  /** Supprime une liste et toutes ses tâches. */
  deleteList(list: TodoList): void {
    this.writableLists.update((lists) => lists.filter((current) => current.id !== list.id));
    this.sync();
  }

  /** Ajoute à une liste une tâche, non réalisée. */
  addTask(list: TodoList, title: string): void {
    const task: Task = { id: this.newId(), title: title, done: false };
    this.updateTasks(list, (tasks) => [...tasks, task]);
    this.sync();
  }

  /** Coche la tâche si elle ne l'était pas, et inversement. */
  toggleTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) =>
      tasks.map((current) =>
        current.id === task.id ? { ...current, done: !current.done } : current,
      ),
    );
    this.sync();
  }

  /** Supprime une tâche de sa liste. */
  deleteTask(list: TodoList, task: Task): void {
    this.updateTasks(list, (tasks) => tasks.filter((current) => current.id !== task.id));
    this.sync();
  }

  /** Fait disparaître le message d'erreur (bouton ✕ du bandeau). */
  clearError(): void {
    this.writableError.set(null);
  }

  /** Envoie TOUTES les listes au serveur : PUT /api/sync, avec le tableau des listes en corps. */
  sync(): void {
    if (!this.loaded) {
      return;
    }
    if (this.sending) {
      this.changedWhileSending = true;
      return;
    }
    this.sending = true;
    this.writableSaving.set(true);
    this.http.put('/api/sync', this.writableLists()).subscribe({
      next: () => {
        this.writableError.set(null);
        this.sendFinished();
      },
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.sendFinished();
      },
    });
  }

  /** Appelé à la fin d'un envoi, réussi ou non : renvoie si les listes ont changé entre-temps. */
  private sendFinished(): void {
    this.sending = false;
    if (this.changedWhileSending) {
      this.changedWhileSending = false;
      this.sync();
    } else {
      this.writableSaving.set(false);
    }
  }

  /** Un numéro pour une nouvelle liste ou tâche : le plus grand numéro déjà utilisé, plus 1. */
  private newId(): number {
    const ids = this.writableLists().flatMap((list) => [
      list.id,
      ...list.tasks.map((task) => task.id),
    ]);
    return Math.max(0, ...ids) + 1;
  }

  /** Range dans le signal error un message compréhensible (voir error-message.ts). */
  private showError(error: HttpErrorResponse): void {
    // 401 : le serveur ne reconnaît plus notre jeton (session expirée, compte supprimé depuis
    // un autre onglet…). On oublie la session : l'utilisateur devra se reconnecter.
    if (error.status === 401) {
      this.auth.forget();
    }
    this.writableError.set(errorMessage(error));
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
