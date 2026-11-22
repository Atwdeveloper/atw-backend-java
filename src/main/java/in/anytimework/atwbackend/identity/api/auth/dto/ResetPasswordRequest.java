package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Sets a new password from a Forgot Password reset link. Revokes all of that "
        + "admin's existing sessions on success.")
public record ResetPasswordRequest(
        @Schema(description = "Opaque token from the token= query parameter of the emailed reset link "
                + "(not a JWT). Single-use.",
                example = "9c8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b")
        @NotBlank String token,

        @Schema(description = "The new password.", example = "NewStrongPassword@123")
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
