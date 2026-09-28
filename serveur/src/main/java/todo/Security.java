package todo;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Outils de sécurité pour les comptes : empreinte des mots de passe et jetons de session.
 *
 * <p>Un mot de passe n'est jamais enregistré tel quel. On enregistre son « empreinte » : le
 * résultat d'un calcul à sens unique (PBKDF2), dont on ne peut pas retrouver le mot de passe. Pour
 * vérifier un mot de passe, on refait le calcul et on compare les empreintes.
 */
final class Security {

  /** Nombre de répétitions du calcul : il rend très lent le fait d'essayer des millions de mots de passe. */
  private static final int PBKDF2_ITERATIONS = 600_000;

  private static final SecureRandom RANDOM = new SecureRandom();

  private Security() {}

  /**
   * Un « sel » : des octets aléatoires, différents pour chaque compte, mélangés au mot de passe
   * avant le calcul. Deux comptes au même mot de passe n'ont ainsi pas la même empreinte.
   */
  static byte[] newSalt() {
    return randomBytes(16);
  }

  /** L'empreinte d'un mot de passe, avec le sel de son compte. */
  static byte[] hashPassword(String password, byte[] salt) throws GeneralSecurityException {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
  }

  /** Vrai si le mot de passe correspond à l'empreinte enregistrée. */
  static boolean checkPassword(String password, byte[] salt, byte[] expectedHash)
      throws GeneralSecurityException {
    // MessageDigest.isEqual compare en temps constant : la durée ne trahit pas où ça diffère.
    return MessageDigest.isEqual(hashPassword(password, salt), expectedHash);
  }

  /**
   * Un nouveau jeton de session : 32 octets aléatoires, écrits en texte (base64url). C'est ce
   * « badge » que le navigateur renvoie ensuite dans l'en-tête Authorization.
   */
  static String newToken() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
  }

  /**
   * L'empreinte SHA-256 d'un jeton. La base ne garde que cette empreinte : même si quelqu'un
   * lisait la base, il ne pourrait pas s'en servir pour se faire passer pour un utilisateur.
   */
  static byte[] sha256(String value) throws GeneralSecurityException {
    return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
  }

  private static byte[] randomBytes(int length) {
    byte[] bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
