package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requests a password-reset email. Always succeeds (200) whether or not the "
        + "address belongs to an admin - deliberate anti-enumeration behavior, not a bug.")
public record ForgotPasswordRequest(
        @Schema(description = "The admin account's email address.", example = "admin@anytimework.in")
        @NotBlank @Email String email
) {
}
