package todo;

/**
 * Erreur à renvoyer au navigateur, avec son code HTTP (400 requête invalide, 404 introuvable…).
 *
 * <p>Le serveur la transforme en réponse JSON : {@code {"error": "le message"}}.
 */
public class ApiException extends Exception {

  private final int status;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** Code HTTP de la réponse. */
  public int status() {
    return status;
  }
}
