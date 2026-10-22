package in.anytimework.atwbackend.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates cryptographically-random opaque tokens (for refresh / reset / invite) and hashes them
 * with SHA-256 for storage. We store only the hash — the plaintext token is shown to the client once.
 */
public final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {
    }

    /** A URL-safe 256-bit random token. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex (64 chars) of the token, for lookup/storage. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
