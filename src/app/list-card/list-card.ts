import { Component, input, output, signal } from '@angular/core';
import { Task, TodoList } from '../todo';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-list-card',
  imports: [FormsModule],
  templateUrl: './list-card.html',
  styleUrl: './list-card.css',
})
export class ListCard {
  /** Entrée : la liste à afficher, fournie par le parent. */
  readonly list = input.required<TodoList>();

  /** Sortie : prévient le parent qu'il faut cocher ou décocher une tâche. */
  readonly toggleTask = output<Task>();

  /** Sortie : prévient le parent qu'il faut supprimer une tâche. */
  readonly deleteTask = output<Task>();

  /** Sortie : prévient le parent qu'il faut supprimer la liste. Elle n'envoie rien. */
  readonly deleteList = output<void>();

  /** Sortie : demande au parent d'ajouter une tâche. Elle envoie le titre saisi. */
  readonly addTask = output<string>();

  /** Le texte tapé dans le champ « Nouvelle tâche ». */
  protected readonly newTaskTitle = signal('');

  /** Appelé à l'envoi du formulaire : transmet le titre au parent, puis vide le champ. */
  protected submitTask(): void {
    const title = this.newTaskTitle().trim();
    if (!title) {
      return;
    }
    this.addTask.emit(title);
    this.newTaskTitle.set('');
  }
}
