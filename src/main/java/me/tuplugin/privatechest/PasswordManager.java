package me.tuplugin.privatechest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Manages password hashing and verification for PrivateChest.
 * Uses SHA-256 with random salt for secure password storage.
 */
public class PasswordManager {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final String SEPARATOR = ":";

    /**
     * Hashes a password with a random salt.
     * @param password The plain text password to hash.
     * @return A string containing salt:hash, or null if hashing fails.
     */
    public static String hashPassword(String password) {
        try {
            // Generate random salt
            byte[] salt = generateSalt();

            // Hash password with salt
            String hash = hashWithSalt(password, salt);
            if (hash == null) return null;

            // Return salt:hash format
            return bytesToHex(salt) + SEPARATOR + hash;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifies if a password matches the stored hash.
     * @param password The plain text password to verify.
     * @param storedHash The stored hash in salt:hash format.
     * @return true if password matches, false otherwise.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null) return false;

        try {
            // Split stored hash into salt and hash parts
            String[] parts = storedHash.split(SEPARATOR, 2);
            if (parts.length != 2) return false;

            byte[] salt = hexToBytes(parts[0]);
            String expectedHash = parts[1];

            // Hash the provided password with the stored salt
            String actualHash = hashWithSalt(password, salt);

            // Compare hashes
            return expectedHash != null && expectedHash.equals(actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a stored password is in plain text (legacy format).
     * @param storedPassword The stored password string.
     * @return true if it's plain text, false if it's hashed.
     */
    public static boolean isPlainText(String storedPassword) {
        return storedPassword != null && !storedPassword.contains(SEPARATOR);
    }

    /**
     * Migrates a plain text password to hashed format.
     * @param plainPassword The plain text password.
     * @return The hashed password, or null if migration fails.
     */
    public static String migratePlainPassword(String plainPassword) {
        return hashPassword(plainPassword);
    }

    // --- Private Helper Methods ---

    /**
     * Generates a random salt.
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Hashes a password with the given salt.
     */
    private static String hashWithSalt(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);

            // Add salt to digest
            digest.update(salt);

            // Add password to digest
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            return bytesToHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Converts hexadecimal string to byte array.
     */
    private static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}