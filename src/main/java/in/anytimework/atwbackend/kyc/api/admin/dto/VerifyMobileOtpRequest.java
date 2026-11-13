package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyMobileOtpRequest(
        @Schema(description = "The id returned by Send Mobile OTP (MobileOtpChallengeDto.otpId).",
                example = "9c3d1b6a-0e5f-4b7c-9a2d-1f6e8c4d5b03", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID otpId,

        @Schema(description = "The 6-digit code sent by SMS. In local/dev (default atw.sms.provider=log) it is "
                + "never returned by this API - read it from the application log instead (logged as '[DEV SMS] "
                + "to=... ').",
                example = "482913", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 10) String code) {
}
