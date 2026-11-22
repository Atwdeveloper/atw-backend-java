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
 * Deterministic HMAC-SHA256 of a company identity number (GST/PAN), used only for a duplicate-number
 * check across companies ({@code Company.gstNumberHash}/{@code panNumberHash}) without ever
 * decrypting the encrypted value to search it. Same algorithm and normalization as
 * {@link IdentityNumberHasher}, but deliberately its own class keyed with the company-PII key
 * ({@link CompanyPiiEncryptionProperties}) rather than reusing {@link IdentityNumberHasher} itself.
 *
 * <p><strong>Why a separate class instead of reusing {@code IdentityNumberHasher}:</strong> that
 * class's constructor is hardwired specifically to {@link WorkerPiiEncryptionProperties} - it is not
 * a generic "hash with whichever key the caller wants" utility despite its name, so calling it from
 * the company module would either (a) silently hash company GST/PAN numbers with the worker-PII key
 * (reusing another dataset's key, exactly the cross-contamination {@link PiiEncryptionConfig}'s
 * separate-key-per-dataset design exists to prevent), or (b) require changing
 * {@code IdentityNumberHasher} into a multi-bean class and updating every existing caller in
 * {@code kyc}/{@code worker} to add an explicit {@code @Qualifier} - a wider refactor of working,
 * already-tested code this slice does not touch without separate approval. A small, additive sibling
 * class - identical in shape, independently keyed - costs nothing and keeps the "one key, one
 * hasher, per dataset" property every other PII dataset in this codebase already has.</p>
 */
@Component
public class CompanyIdentityNumberHasher {

    private final SecretKeySpec key;

    CompanyIdentityNumberHasher(CompanyPiiEncryptionProperties properties) {
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
