package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.shared.security.WorkerPiiEncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic HMAC-SHA256 of a worker identity number (Aadhaar, PAN, ...), used only for a
 * duplicate-number check across workers (e.g. {@code WorkerKycProfile.aadhaarNumberHash}/
 * {@code panNumberHash}) without ever decrypting the encrypted value to search it. Keyed with the
 * same worker-PII key used for encryption ({@link WorkerPiiEncryptionProperties}): a cryptographically
 * independent use of the same key material (confidentiality via AES-GCM elsewhere, a keyed one-way
 * hash here), avoiding a third secret to provision and rotate.
 *
 * <p>Originally {@code AadhaarHasher}, Aadhaar-only; renamed and reused as-is when the identical
 * duplicate-check need arose for PAN - the algorithm (and the normalization below: strip whitespace,
 * uppercase) has nothing Aadhaar-specific about it.</p>
 */
@Component
class IdentityNumberHasher {

    private final SecretKeySpec key;

    IdentityNumberHasher(WorkerPiiEncryptionProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.key()), "HmacSHA256");
    }

    String hash(String identityNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(normalize(identityNumber).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to hash identity number", e);
        }
    }

    private static String normalize(String identityNumber) {
        return identityNumber == null ? "" : identityNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
