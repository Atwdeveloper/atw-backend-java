package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No format/checksum validation on the number itself - only a length cap, matching
 * {@code SubmitAadhaarRequest}'s precedent ("avoid rejecting legitimate input on a guessed-at
 * pattern") rather than PAN's (whose format is an official, stable, published standard).
 */
public record SubmitWorkerUanRequest(
        @Schema(description = "Universal Account Number (EPFO), recorded as declared - no format "
                + "validation, only a length cap.", example = "123456789012",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 20) String uanNumber
) {
}
