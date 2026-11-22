package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No format/checksum validation on the number itself - only a length cap, to avoid rejecting legitimate input on a guessed-at pattern. */
public record SubmitAadhaarRequest(
        @Schema(description = "Aadhaar number, recorded as declared and left unverified (PENDING). No "
                + "format/checksum validation - only the length cap (unlike PAN, which has a stable published "
                + "client-side format - see Submit PAN).",
                example = "234123412346", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 20) String aadhaarNumber) {
}
