package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Exchanges a refresh token for a new access+refresh token pair, rotating the refresh token.")
public record RefreshRequest(
        @Schema(description = "The opaque refresh token to redeem (from a prior Verify OTP/Refresh "
                + "response - not a JWT). Single-use: presenting it again after it has rotated is "
                + "treated as token theft and revokes the whole session family.",
                example = "7f3a9c2e8b1d4f6a0c5e9b2d7a1f4c8e6b3d0a9f2c5e8b1d4f7a0c3e6b9d2f5a")
        @NotBlank String refreshToken
) {
}
