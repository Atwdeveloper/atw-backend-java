package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyAadhaarOtpRequest(
        @Schema(description = "The id returned by Send Aadhaar OTP (AadhaarOtpChallengeDto.verificationId).",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID verificationId,

        @Schema(description = "The OTP code. With the default local stub Aadhaar e-KYC provider, the only code "
                + "ever accepted is the fixed value 123456; any other value fails as \"Invalid or expired OTP\".",
                example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 10) String otp) {
}
