package in.anytimework.atwbackend.kyc;

import in.anytimework.atwbackend.kyc.api.admin.dto.SubmitPanRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Bean Validation unit test (no Spring context) for the PAN format check added 2026-11-11 -
 * confirmed with the developer as a deliberate departure from {@code SubmitAadhaarRequest}'s
 * identical-looking but intentionally format-free validation (see both DTOs' Javadoc).
 */
class SubmitPanRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validUppercasePan_hasNoViolations() {
        Set<ConstraintViolation<SubmitPanRequest>> violations = validator.validate(new SubmitPanRequest("ABCPE1234F"));
        assertThat(violations).isEmpty();
    }

    @Test
    void lowercaseInput_isNormalizedToUppercaseAndAccepted() {
        SubmitPanRequest request = new SubmitPanRequest("abcpe1234f");

        assertThat(request.panNumber()).isEqualTo("ABCPE1234F");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void wrongShape_isRejected() {
        assertThat(validator.validate(new SubmitPanRequest("ABCPE123F"))).isNotEmpty(); // only 3 digits
        assertThat(validator.validate(new SubmitPanRequest("ABCP1234EF"))).isNotEmpty(); // digit in letter position
        assertThat(validator.validate(new SubmitPanRequest("ABCPE12345"))).isNotEmpty(); // trailing digit, not a letter
    }

    @Test
    void blank_isRejected() {
        assertThat(validator.validate(new SubmitPanRequest(""))).isNotEmpty();
    }
}
