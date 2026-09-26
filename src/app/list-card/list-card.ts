import { Component, computed, input, output, signal } from '@angular/core';
import { Task, TodoList } from '../todo';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-list-card',
  imports: [FormsModule],
  templateUrl: './list-card.html',
  styleUrl: './list-card.css',
})
export class ListCard {
  /** Vrai quand la liste est terminée : au moins une tâche, et toutes réalisées. */
  protected readonly completed = computed(() => {
    const tasks = this.list().tasks;
    return tasks.length > 0 && tasks.every((task) => task.done);
  });

  /** Nombre de tâches réalisées, affiché à côté du nom (par exemple « 1 / 3 »). */
  protected readonly doneCount = computed(
    () => this.list().tasks.filter((task) => task.done).length,
  );

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
