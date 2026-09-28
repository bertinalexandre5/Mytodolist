import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Task, TodoList } from '../todo';
import { ListCard } from './list-card';

describe('ListCard', () => {
  let fixture: ComponentFixture<ListCard>;
  let element: HTMLElement;

  /** Affiche une carte pour cette liste, et attend la fin de l'affichage. */
  async function show(list: TodoList): Promise<void> {
    fixture = TestBed.createComponent(ListCard);
    fixture.componentRef.setInput('list', list);
    await fixture.whenStable();
    element = fixture.nativeElement;
  }

  /** Vrai si le nom de la liste est barré (classe CSS « done » sur le titre). */
  function nameIsCrossedOut(): boolean {
    return element.querySelector('h2')!.classList.contains('done');
  }

  const pain: Task = { id: 1, title: 'Pain', done: true };
  const lait: Task = { id: 2, title: 'Lait', done: false };

  describe('la règle du nom barré', () => {
    it('barre le nom quand toutes les tâches sont réalisées', async () => {
      await show({ id: 1, name: 'Courses', tasks: [pain, { ...lait, done: true }] });

      expect(nameIsCrossedOut()).toBe(true);
      expect(element.querySelector('.count')!.textContent).toContain('2 / 2');
    });

    it("ne barre pas le nom s'il reste une tâche à faire", async () => {
      await show({ id: 1, name: 'Courses', tasks: [pain, lait] });

      expect(nameIsCrossedOut()).toBe(false);
      expect(element.querySelector('.count')!.textContent).toContain('1 / 2');
    });

    it("ne barre pas le nom d'une liste vide", async () => {
      await show({ id: 1, name: 'Courses', tasks: [] });

      expect(nameIsCrossedOut()).toBe(false);
      expect(element.textContent).toContain('Aucune tâche');
      expect(element.querySelector('.count')).toBeNull();
    });

    it("débarre le nom dès qu'on ajoute une tâche", async () => {
      await show({ id: 1, name: 'Courses', tasks: [pain] });
      expect(nameIsCrossedOut()).toBe(true);

      fixture.componentRef.setInput('list', { id: 1, name: 'Courses', tasks: [pain, lait] });
      await fixture.whenStable();

      expect(nameIsCrossedOut()).toBe(false);
    });
  });

  describe('les tâches', () => {
    it('barre les tâches réalisées, et elles seules', async () => {
      await show({ id: 1, name: 'Courses', tasks: [pain, lait] });

      const labels = element.querySelectorAll('label');
      expect(labels[0].classList.contains('done')).toBe(true);
      expect(labels[1].classList.contains('done')).toBe(false);
    });

    it('prévient le parent quand on coche une tâche', async () => {
      await show({ id: 1, name: 'Courses', tasks: [pain, lait] });
      let received: Task | undefined;
      fixture.componentInstance.toggleTask.subscribe((task) => (received = task));

      element.querySelectorAll<HTMLInputElement>('input[type=checkbox]')[1].click();

      expect(received).toEqual(lait);
    });

    it('envoie le titre saisi au parent, puis vide le champ', async () => {
      await show({ id: 1, name: 'Courses', tasks: [] });
      let received: string | undefined;
      fixture.componentInstance.addTask.subscribe((title) => (received = title));

      const field = element.querySelector<HTMLInputElement>('.new-task input')!;
      field.value = '  Fromage  ';
      field.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      element.querySelector<HTMLButtonElement>('.new-task button')!.click();
      await fixture.whenStable();

      expect(received).toBe('Fromage');
      expect(field.value).toBe('');
    });
  });
});
