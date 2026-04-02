package org.example.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Small AES-GCM helper for encrypting sensitive metadata stored in SQLite.
 *
 * Encrypted payload format:
 *   v1:Base64(nonce(12 bytes) + ciphertextWithTag)
 *
 * Backward compatibility:
 * - If a value does not start with the {@link #ENC_PREFIX}, it is treated as plaintext.
 * - If decryption fails, we fall back to returning the original input unchanged.
 */
public final class CryptoUtil {

    private static final String ENC_PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private static final Path KEY_FILE = Paths.get("data", "crypto.key");
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile SecretKeySpec cachedKey;

    private CryptoUtil() {}

    public static String encryptToString(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.startsWith(ENC_PREFIX)) {
            // Avoid double-encrypting already-encrypted values.
            return plaintext;
        }
        try {
            SecretKeySpec key = getOrCreateKey();
            byte[] nonce = new byte[NONCE_BYTES];
            RNG.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            String b64 = Base64.getEncoder().encodeToString(combined);
            return ENC_PREFIX + b64;
        } catch (Exception e) {
            // Best-effort: if crypto setup fails, keep the app functional.
            return plaintext;
        }
    }

    public static String decryptToString(String encryptedOrPlain) {
        if (encryptedOrPlain == null) {
            return null;
        }
        if (!encryptedOrPlain.startsWith(ENC_PREFIX)) {
            return encryptedOrPlain; // plaintext legacy
        }
        try {
            SecretKeySpec key = getOrCreateKey();
            String b64 = encryptedOrPlain.substring(ENC_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(b64);
            if (combined.length < NONCE_BYTES + 1) {
                return encryptedOrPlain;
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(combined, NONCE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Backward compatibility: if decryption fails, treat as plaintext.
            return encryptedOrPlain;
        }
    }

    private static SecretKeySpec getOrCreateKey() throws Exception {
        if (cachedKey != null) {
            return cachedKey;
        }
        synchronized (CryptoUtil.class) {
            if (cachedKey != null) {
                return cachedKey;
            }

            String provided = System.getProperty("app.cryptoKey", "");
            byte[] keyBytes;
            if (provided != null && !provided.isBlank()) {
                // Derive a stable AES key from the provided value.
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                keyBytes = sha256.digest(provided.getBytes(StandardCharsets.UTF_8));
            } else {
                // Persist a generated key locally so restore across runs still works.
                if (Files.isRegularFile(KEY_FILE)) {
                    String raw = Files.readString(KEY_FILE, StandardCharsets.UTF_8).trim();
                    byte[] decoded = Base64.getDecoder().decode(raw);
                    if (decoded.length != KEY_BYTES) {
                        throw new IllegalStateException("Invalid crypto key length");
                    }
                    keyBytes = decoded;
                } else {
                    keyBytes = new byte[KEY_BYTES];
                    RNG.nextBytes(keyBytes);

                    Path dir = KEY_FILE.getParent();
                    if (dir != null) {
                        Files.createDirectories(dir);
                    }
                    String b64 = Base64.getEncoder().encodeToString(keyBytes);
                    Files.writeString(KEY_FILE, b64, StandardCharsets.UTF_8);
                }
            }

            cachedKey = new SecretKeySpec(keyBytes, "AES");
            return cachedKey;
        }
    }
}

