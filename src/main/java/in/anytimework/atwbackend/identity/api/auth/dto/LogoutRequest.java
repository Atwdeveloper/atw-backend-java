package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Revokes a refresh token (logout). Always succeeds, even for an unknown or "
        + "already-revoked token.")
public record LogoutRequest(
        @Schema(description = "The opaque refresh token to revoke (from a prior Verify OTP/Refresh "
                + "response - not a JWT).",
                example = "7f3a9c2e8b1d4f6a0c5e9b2d7a1f4c8e6b3d0a9f2c5e8b1d4f7a0c3e6b9d2f5a")
        @NotBlank String refreshToken
) {
}
