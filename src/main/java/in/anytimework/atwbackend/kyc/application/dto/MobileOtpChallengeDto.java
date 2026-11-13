package in.anytimework.atwbackend.kyc.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record MobileOtpChallengeDto(
        @Schema(description = "Id of this mobile-OTP challenge. Pass it back as otpId to Verify Mobile OTP.",
                example = "9c3d1b6a-0e5f-4b7c-9a2d-1f6e8c4d5b03")
        UUID otpId,

        @Schema(description = "How many seconds until the OTP expires.", example = "600")
        int expiresInSeconds) {
}
