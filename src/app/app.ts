import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ListCard } from './list-card/list-card';
import { TodoList } from './todo';
import { TodoStore } from './todo-store';

@Component({
  selector: 'app-root',
  imports: [FormsModule, ListCard],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  /** Le service qui détient les listes : Angular nous donne son exemplaire unique. */
  protected readonly store = inject(TodoStore);

  protected readonly title = 'Mes listes de tâches';

  /** Le texte tapé dans le champ « Nom de la nouvelle liste ». */
  protected readonly newListName = signal('');

  /** Crée une liste portant le nom saisi, puis vide le champ. */
  protected createList(): void {
    const name = this.newListName().trim();
    if (!name) {
      return;
    }
    this.store.createList(name);
    this.newListName.set('');
  }

  /** Supprime une liste, après confirmation. */
  protected deleteList(list: TodoList): void {
    if (confirm(`Supprimer la liste « ${list.name} » et toutes ses tâches ?`)) {
      this.store.deleteList(list);
    }
  }
}
