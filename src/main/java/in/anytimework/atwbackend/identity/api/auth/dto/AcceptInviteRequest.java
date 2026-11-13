package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Completes a staff invite by setting that brand-new account's first real password.")
public record AcceptInviteRequest(
        @Schema(description = "Opaque token from the token= query parameter of the invite email "
                + "(not a JWT). Single-use.",
                example = "4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b")
        @NotBlank String token,

        @Schema(description = "The account's first real password.", example = "NewStrongPassword@123")
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
