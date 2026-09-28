import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthStore } from '../auth-store';
import { errorMessage } from '../error-message';
import { TodoStore } from '../todo-store';

@Component({
  selector: 'app-account-panel',
  imports: [FormsModule],
  templateUrl: './account-panel.html',
  styleUrl: './account-panel.css',
})
export class AccountPanel {
  protected readonly auth = inject(AuthStore);
  private readonly todos = inject(TodoStore);

  /** Ce qui est tapé dans les champs Identifiant et Mot de passe. */
  protected readonly username = signal('');
  protected readonly password = signal('');

  /** Le message d'erreur du panneau (mot de passe incorrect…), ou null. */
  protected readonly message = signal<string | null>(null);

  /** Vrai pendant une requête : les boutons sont désactivés, pour éviter un double envoi. */
  protected readonly busy = signal(false);

  /** Se connecter, puis afficher les listes du compte. */
  protected login(): void {
    this.busy.set(true);
    this.message.set(null);
    this.auth.login(this.username(), this.password()).subscribe({
      next: () => {
        this.busy.set(false);
        this.password.set('');
        this.todos.load();
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.message.set(errorMessage(error));
      },
    });
  }

  /** Créer un compte, puis y enregistrer les listes affichées. */
  protected register(): void {
    this.busy.set(true);
    this.message.set(null);
    this.auth.register(this.username(), this.password()).subscribe({
      next: () => {
        this.busy.set(false);
        this.password.set('');
        this.todos.sync();
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.message.set(errorMessage(error));
      },
    });
  }

  /** Se déconnecter, puis revenir aux valeurs de départ du mode test. */
  protected logout(): void {
    this.auth.logout();
    this.todos.load();
  }

  /** Supprimer son compte et ses listes, après confirmation. */
  protected deleteAccount(): void {
    if (!confirm(`Supprimer le compte « ${this.auth.username()} » et toutes ses listes ?`)) {
      return;
    }
    this.message.set(null);
    this.auth.deleteAccount().subscribe({
      next: () => this.todos.load(),
      error: (error: HttpErrorResponse) => this.message.set(errorMessage(error)),
    });
  }
}
