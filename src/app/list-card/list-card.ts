import { Component, input, output } from '@angular/core';
import { Task, TodoList } from '../todo';

@Component({
  selector: 'app-list-card',
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
}
