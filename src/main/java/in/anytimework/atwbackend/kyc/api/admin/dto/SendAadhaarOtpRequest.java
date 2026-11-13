package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** consentGiven must be explicitly true - the service rejects false/omitted rather than assuming consent. */
public record SendAadhaarOtpRequest(
        @Schema(description = "Aadhaar number to send the e-KYC OTP to. Deliberately NO format/checksum "
                + "validation - only the length cap - so legitimate input is never rejected on a guessed-at pattern.",
                example = "234123412346", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 20) String aadhaarNumber,

        @Schema(description = "Must be literally true. false or omitted is rejected outright with a validation "
                + "error - consent is never inferred from the call simply being made.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean consentGiven) {
}
