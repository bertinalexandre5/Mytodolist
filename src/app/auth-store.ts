import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

/** Ce que le serveur renvoie quand on se connecte : un jeton, et l'identifiant du compte. */
export interface Session {
  token: string;
  username: string;
}

/** Nom sous lequel la session est rangée dans le navigateur (localStorage). */
const STORAGE_KEY = 'todolist-session';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);

  /** La session en cours, ou null si l'on n'est pas connecté. Relue au démarrage. */
  private readonly session = signal<Session | null>(readStoredSession());

  /** L'identifiant du compte connecté, ou null. */
  readonly username = computed(() => this.session()?.username ?? null);

  /** Le jeton de la session, ou null. */
  readonly token = computed(() => this.session()?.token ?? null);

  /** Se connecter : POST /api/sessions → réponse : {token, username}. */
  login(username: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/sessions', { username: username, password: password })
      .pipe(tap((session) => this.remember(session)));
  }

  /** Créer un compte : POST /api/accounts. Le serveur y connecte aussitôt. */
  register(username: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/accounts', { username: username, password: password })
      .pipe(tap((session) => this.remember(session)));
  }

  /** Supprimer son compte et ses listes : DELETE /api/accounts/me, puis oublier la session. */
  deleteAccount(): Observable<unknown> {
    return this.http.delete('/api/accounts/me').pipe(tap(() => this.forget()));
  }

  /** Se déconnecter : on oublie la session tout de suite, puis on prévient le serveur. */
  logout(): void {
    const token = this.token();
    this.forget();
    if (token !== null) {
      this.http
        .delete('/api/sessions/current', { headers: { Authorization: `Bearer ${token}` } })
        .subscribe({ error: () => {} });
    }
  }

  /** Oublie la session, dans le signal comme dans le navigateur. */
  forget(): void {
    this.session.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  /** Garde la session, dans le signal et dans le navigateur (en texte JSON). */
  private remember(session: Session): void {
    this.session.set(session);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

/** Relit la session rangée dans le navigateur, s'il y en a une (null sinon). */
function readStoredSession(): Session | null {
  try {
    const text = localStorage.getItem(STORAGE_KEY);
    return text === null ? null : JSON.parse(text);
  } catch {
    return null;
  }
}
