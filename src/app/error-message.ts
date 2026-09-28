import { HttpErrorResponse } from '@angular/common/http';

/** Transforme une erreur HTTP en message compréhensible pour l'utilisateur. */
export function errorMessage(error: HttpErrorResponse): string {
  const serverMessage = error.error?.error;
  if (typeof serverMessage === 'string') {
    return serverMessage;
  }
  return 'Impossible de joindre le serveur. Est-il bien lancé ?';
}
