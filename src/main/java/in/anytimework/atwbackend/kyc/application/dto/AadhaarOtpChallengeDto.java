package in.anytimework.atwbackend.kyc.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AadhaarOtpChallengeDto(
        @Schema(description = "Id of this Aadhaar verification attempt. Pass it back as verificationId to "
                + "Verify Aadhaar OTP, and - once that succeeds - as aadhaarVerificationId when creating the worker.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID verificationId,

        @Schema(description = "How many seconds until the OTP expires.", example = "600")
        int expiresInSeconds) {
}
