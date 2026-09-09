package me.tuplugin.privatechest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.regex.Pattern;

/**
 * Hashes and verifies container passwords.
 *
 * <p>3.0 stores passwords as PBKDF2-HMAC-SHA256 with a random salt and a configurable iteration
 * count. Earlier versions used a single pass of salted SHA-256, which is fast enough to brute force
 * offline if the data file is ever exposed. A single hash pass is the wrong primitive for a password
 * regardless of what it protects.</p>
 *
 * <p>Three formats are readable, so no data conversion is needed:</p>
 * <ul>
 *     <li>{@code pbkdf2:iterations:salt:hash} — written by 3.0 and later</li>
 *     <li>{@code salt:hash} — salted SHA-256, written by 2.0 through 2.3</li>
 *     <li>anything else — plain text, written before 2.0</li>
 * </ul>
 *
 * <p>Older formats are rewritten to the current one the first time a password is verified
 * successfully, so a server upgrades itself as players use it, with no bulk rehashing pause.</p>
 *
 * @since 2.0, reworked in 3.0
 */
public final class PasswordManager {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String LEGACY_ALGORITHM = "SHA-256";
    private static final String PBKDF2_PREFIX = "pbkdf2";
    private static final String SEPARATOR = ":";

    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH_BITS = 256;

    public static final int DEFAULT_ITERATIONS = 20_000;
    private static final int MIN_ITERATIONS = 1_000;
    private static final int MAX_ITERATIONS = 1_000_000;

    /** Salted SHA-256 as written by 2.0 through 2.3: 16 salt bytes and a 32 byte hash, hex encoded. */
    private static final Pattern LEGACY_FORMAT = Pattern.compile("^[0-9a-fA-F]{32}:[0-9a-fA-F]{64}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile int iterations = DEFAULT_ITERATIONS;

    private PasswordManager() {
        // Utility class.
    }

    /**
     * Applies the configured iteration count.
     *
     * <p>Higher values cost more to attack and more to verify. The verification happens once per
     * command, so the practical ceiling is however long an admin is willing to let a
     * {@code /unlockchest} take.</p>
     *
     * @param configured value from config.yml
     * @return the value actually applied after clamping
     */
    public static int configure(int configured) {
        int clamped = Math.max(MIN_ITERATIONS, Math.min(MAX_ITERATIONS, configured));
        iterations = clamped;
        return clamped;
    }

    public static int getIterations() {
        return iterations;
    }

    /**
     * Hashes a password for storage.
     *
     * @return the encoded digest, or null if hashing failed
     */
    public static String hashPassword(String password) {
        if (password == null) {
            return null;
        }

        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        int rounds = iterations;
        byte[] hash = pbkdf2(password, salt, rounds);
        if (hash == null) {
            return null;
        }

        return PBKDF2_PREFIX + SEPARATOR + rounds + SEPARATOR + toHex(salt) + SEPARATOR + toHex(hash);
    }

    /**
     * Verifies a password against any supported stored format.
     */
    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }

        if (stored.startsWith(PBKDF2_PREFIX + SEPARATOR)) {
            return verifyPbkdf2(password, stored);
        }
        if (LEGACY_FORMAT.matcher(stored).matches()) {
            return verifyLegacySha256(password, stored);
        }

        // Plain text from before 2.0. Compared without short-circuiting on length.
        return MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Whether a stored value should be rewritten in the current format.
     *
     * <p>True for plain text, for salted SHA-256, and for PBKDF2 digests written with fewer iterations
     * than are configured now, so raising the iteration count strengthens existing passwords as they
     * are used.</p>
     */
    public static boolean needsRehash(String stored) {
        if (stored == null) {
            return false;
        }
        if (!stored.startsWith(PBKDF2_PREFIX + SEPARATOR)) {
            return true;
        }

        String[] parts = stored.split(SEPARATOR);
        if (parts.length != 4) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < iterations;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Whether a stored value is still the plain text written before 2.0.
     *
     * <p>The format is matched exactly rather than by looking for a separator character. The 2.x check
     * treated any value containing a colon as already hashed, so a legacy password that happened to
     * contain a colon was never upgraded and could never be verified again, permanently locking its
     * owner out of their own container.</p>
     */
    public static boolean isLegacyPlainText(String stored) {
        if (stored == null) {
            return false;
        }
        return !stored.startsWith(PBKDF2_PREFIX + SEPARATOR) && !LEGACY_FORMAT.matcher(stored).matches();
    }

    // --- Internals ---

    private static boolean verifyPbkdf2(String password, String stored) {
        String[] parts = stored.split(SEPARATOR);
        if (parts.length != 4) {
            return false;
        }

        try {
            int rounds = Integer.parseInt(parts[1]);
            byte[] salt = fromHex(parts[2]);
            byte[] expected = fromHex(parts[3]);
            if (salt == null || expected == null) {
                return false;
            }

            byte[] actual = pbkdf2(password, salt, rounds);
            return actual != null && MessageDigest.isEqual(expected, actual);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean verifyLegacySha256(String password, String stored) {
        String[] parts = stored.split(SEPARATOR, 2);
        if (parts.length != 2) {
            return false;
        }

        byte[] salt = fromHex(parts[0]);
        byte[] expected = fromHex(parts[1]);
        if (salt == null || expected == null) {
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(LEGACY_ALGORITHM);
            digest.update(salt);
            byte[] actual = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        } finally {
            spec.clearPassword();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(Character.forDigit((b >> 4) & 0xF, 16));
            result.append(Character.forDigit(b & 0xF, 16));
        }
        return result.toString();
    }

    private static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }

        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }
}
