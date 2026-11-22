package in.anytimework.atwbackend.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests (no Spring context) for {@link CompanyIdentityNumberHasher}'s normalization -
 * the "format handling" half of the company module's GST/PAN duplicate-detection guarantee
 * ({@code Company.gstNumberHash}/{@code panNumberHash}, enforced by {@code CompanyService.create} and
 * backstopped by the partial unique indexes in {@code V37__company.sql}). Same algorithm and
 * normalization contract as {@link IdentityNumberHasher}, just independently keyed - see this class's
 * own Javadoc for why a separate class exists instead of reusing that one.
 */
class CompanyIdentityNumberHasherTest {

    private static final String COMPANY_KEY = "7nBrMK/+GM788wVVkUZwdNer7rip96OMkEwpMSg47mA=";
    private static final String A_DIFFERENT_KEY = "c8ZWWQX6uaMDrKhuKJU8cwyz80sraP4EdBWcKfHZ4WM=";

    private final CompanyIdentityNumberHasher hasher =
            new CompanyIdentityNumberHasher(new CompanyPiiEncryptionProperties(COMPANY_KEY));

    @Test
    void hash_isDeterministic_forTheSameInput() {
        assertThat(hasher.hash("27AAAPG1234L1ZV")).isEqualTo(hasher.hash("27AAAPG1234L1ZV"));
    }

    @Test
    void hash_isCaseAndWhitespaceInsensitive() {
        // The whole point of normalizing before hashing: two on-paper-identical GSTINs/PANs typed
        // with different case or stray whitespace must still collide the duplicate-number check - a
        // case-sensitive hash would silently let the same real-world registration be entered twice
        // under the company table's partial unique index.
        String canonical = hasher.hash("27AAAPG1234L1ZV");
        assertThat(hasher.hash("27aaapg1234l1zv")).isEqualTo(canonical);
        assertThat(hasher.hash(" 27AAAPG1234L1ZV ")).isEqualTo(canonical);
        assertThat(hasher.hash("27 AAAPG 1234 L1ZV")).isEqualTo(canonical);
    }

    @Test
    void hash_differsForDifferentInputs() {
        assertThat(hasher.hash("27AAAPG1234L1ZV")).isNotEqualTo(hasher.hash("27AAAPG1234L1ZW"));
    }

    @Test
    void hash_isIndependentOfAnotherDatasetsKey() {
        // CompanyIdentityNumberHasher exists specifically so company GST/PAN is never hashed with the
        // worker-PII key (see this class's own Javadoc) - confirms it really is independently keyed:
        // a different key must produce a different hash for the identical input.
        CompanyIdentityNumberHasher otherKey = new CompanyIdentityNumberHasher(
                new CompanyPiiEncryptionProperties(A_DIFFERENT_KEY));
        assertThat(hasher.hash("27AAAPG1234L1ZV")).isNotEqualTo(otherKey.hash("27AAAPG1234L1ZV"));
    }
}
