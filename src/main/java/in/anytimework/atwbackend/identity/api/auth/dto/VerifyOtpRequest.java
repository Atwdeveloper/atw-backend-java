package in.anytimework.atwbackend.identity.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VerifyOtpRequest(
        @NotNull UUID challengeId,
        @NotBlank String otp
) {
}
