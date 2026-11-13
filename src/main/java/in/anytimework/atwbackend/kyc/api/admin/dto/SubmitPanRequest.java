package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Unlike {@code SubmitAadhaarRequest} (deliberately format-free - "avoid rejecting legitimate input on
 * a guessed-at pattern"), PAN's 10-character structure is a stable, officially published format (not a
 * guess) the legacy app already validated against - confirmed with the developer 2026-11-11. Always
 * 5 letters, 4 digits, 1 letter (e.g. {@code ABCPE1234F}); case-insensitive input is accepted and
 * normalized to uppercase, matching how {@code IdentityNumberHasher} already normalizes for hashing.
 */
public record SubmitPanRequest(
        @Schema(description = "PAN number, recorded as declared and left unverified (PENDING). Must be 5 "
                + "letters, 4 digits, 1 letter. Case-insensitive input is accepted and normalized to uppercase "
                + "before the format check runs, so lowercase input is also valid.",
                example = "ABCPE1234F", pattern = "^[A-Z]{5}[0-9]{4}[A-Z]$", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN must be 5 letters, 4 digits, 1 letter (e.g. ABCPE1234F)")
        String panNumber
) {
    /** Uppercases before {@code @Pattern} runs, so lowercase/mixed-case input is normalized rather than rejected. */
    public SubmitPanRequest {
        if (panNumber != null) {
            panNumber = panNumber.toUpperCase(java.util.Locale.ROOT);
        }
    }
}
