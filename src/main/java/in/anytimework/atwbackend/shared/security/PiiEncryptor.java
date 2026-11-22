package in.anytimework.atwbackend.shared.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for PII stored at rest (used by {@link EncryptedStringConverter} and
 * {@link WorkerEncryptedStringConverter}). Each value gets a fresh random 12-byte IV, stored
 * alongside the ciphertext (the IV is not secret - GCM's security relies on IV uniqueness per key,
 * not IV secrecy). Output is {@code base64(iv || ciphertext || authTag)}; GCM mode appends the tag
 * to the ciphertext automatically.
 *
 * <p>Plain class, not a {@code @Component}: two independently-keyed instances exist side by side
 * (platform-staff PII vs. worker PII - see {@link PiiEncryptionConfig}), so each is built from an
 * explicit {@code @Bean} method rather than Spring constructing a single instance automatically.</p>
 */
public class PiiEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // AES-256

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key   the AES-256 key, base64-encoded (must decode to exactly 32 bytes)
     * @param propertyName the config property this came from, used only to make a decode failure
     *                     actionable (e.g. {@code "atw.security.pii-encryption.key"})
     */
    public PiiEncryptor(String base64Key, String propertyName) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(propertyName + " must decode to exactly "
                    + KEY_LENGTH_BYTES + " bytes (AES-256); got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to decrypt value - wrong key, or data corrupted/tampered with", e);
        }
    }
}
