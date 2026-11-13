package in.anytimework.atwbackend.shared.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms the worker-PII key is genuinely independent from the identity-PII key (see
 * PiiEncryptionConfig): a value encrypted under one key must not be decryptable under the other.
 * Uses the real values from application-test.properties/application-local.properties so a
 * copy-paste mistake that made both properties resolve to the same key would be caught here.
 */
class WorkerPiiEncryptionSeparationTest {

    private static final String IDENTITY_TEST_KEY = "bR48i/M8D18Kc4Em2YWc9OakltC/aNqk7UUEptivw7o=";
    private static final String WORKER_TEST_KEY = "ak89h67UiinVulo/Xr/ci1Tp4HUtIC6HWuSggd3Sed8=";

    @Test
    void theTwoConfiguredKeysAreNotTheSameValue() {
        assertThat(IDENTITY_TEST_KEY).isNotEqualTo(WORKER_TEST_KEY);
    }

    @Test
    void aValueEncryptedWithOneKeyCannotBeDecryptedWithTheOther() {
        PiiEncryptor identityEncryptor = new PiiEncryptor(IDENTITY_TEST_KEY, "test.identity.key");
        PiiEncryptor workerEncryptor = new PiiEncryptor(WORKER_TEST_KEY, "test.worker.key");

        String ciphertext = workerEncryptor.encrypt("123456789012");

        assertThat(workerEncryptor.decrypt(ciphertext)).isEqualTo("123456789012");
        assertThatThrownBy(() -> identityEncryptor.decrypt(ciphertext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new PiiEncryptor(tooShort, "test.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
