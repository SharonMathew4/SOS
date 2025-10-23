import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class PasswordUtil {
    private static final int SALT_LEN = 16; // bytes
    private static final int ITERATIONS = 65536;
    private static final int KEY_LEN = 256; // bits
    private static final String ALGO = "PBKDF2WithHmacSHA256";

    public static String hashPassword(char[] password) {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LEN);
        // Store as iterations:salt:hash (Base64)
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(char[] password, String stored) {
        try {
            String[] parts = stored.split(":");
            int iters = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, iters, expected.length * 8);
            return slowEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLenBits) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGO);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 failure: " + e.getMessage(), e);
        }
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
