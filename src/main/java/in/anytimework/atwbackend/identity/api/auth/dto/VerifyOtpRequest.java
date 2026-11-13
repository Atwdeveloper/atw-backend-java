package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Step 2 of admin login: submits the OTP for a challenge from Login or Resend OTP.")
public record VerifyOtpRequest(
        @Schema(description = "The challengeId from the Login/Resend OTP response.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull UUID challengeId,

        @Schema(description = "The one-time code (compared as a string - leading zeros matter).",
                example = "482913")
        @NotBlank String otp
) {
}
