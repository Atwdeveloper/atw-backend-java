package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No format validation - ESIC number formats vary by registration era/scheme, not stable enough to
 * safely guess a single pattern (same reasoning as {@code SubmitAadhaarRequest}).
 */
public record SubmitWorkerEsicRequest(
        @Schema(description = "ESIC number, recorded as declared - no format validation, only a "
                + "length cap.", example = "12345678901234567", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 20) String esicNumber
) {
}
