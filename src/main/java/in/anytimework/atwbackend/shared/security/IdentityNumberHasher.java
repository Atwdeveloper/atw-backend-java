package in.anytimework.atwbackend.shared.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic HMAC-SHA256 of a worker identity number (Aadhaar, PAN, UAN, ESIC, ...), used only for
 * a duplicate-number check across workers (e.g. {@code WorkerKycProfile.aadhaarNumberHash}/
 * {@code panNumberHash}, {@code WorkerStatutoryDetail.uanNumberHash}/{@code esicNumberHash}) without
 * ever decrypting the encrypted value to search it. Keyed with the same worker-PII key used for
 * encryption ({@link WorkerPiiEncryptionProperties}): a cryptographically independent use of the same
 * key material (confidentiality via AES-GCM elsewhere, a keyed one-way hash here), avoiding a third
 * secret to provision and rotate.
 *
 * <p>Originally {@code kyc.application.AadhaarHasher} (Aadhaar-only), then {@code IdentityNumberHasher}
 * when PAN needed the identical duplicate-check logic, then promoted here to {@code shared.security}
 * when the {@code worker} module's own statutory-detail (UAN/ESIC) needed it too - this is generic
 * identity-number hashing with nothing module-specific about it (the algorithm and normalization
 * below - strip whitespace, uppercase - has never had anything Aadhaar- or kyc-specific about it),
 * exactly the kind of technical plumbing {@code shared/} exists for, not a business rule any one
 * module owns.</p>
 */
@Component
public class IdentityNumberHasher {

    private final SecretKeySpec key;

    IdentityNumberHasher(WorkerPiiEncryptionProperties properties) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.key()), "HmacSHA256");
    }

    public String hash(String identityNumber) {
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
