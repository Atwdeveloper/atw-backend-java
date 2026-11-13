package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Requests a fresh OTP for an in-progress login attempt; immediately invalidates "
        + "the previous challenge.")
public record ResendOtpRequest(
        @Schema(description = "The challengeId from the Login (or a previous Resend OTP) response.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull UUID challengeId
) {
}
