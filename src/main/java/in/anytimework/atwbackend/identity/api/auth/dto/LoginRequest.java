package in.anytimework.atwbackend.identity.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Step 1 of admin login: password credentials. A successful call here is always "
        + "followed by Verify OTP, unless OTP is disabled for the environment.")
public record LoginRequest(
        @Schema(description = "Admin's username or email - matched case-insensitively against either field.",
                example = "superadmin@anytimework.in")
        @NotBlank String usernameOrEmail,

        @Schema(description = "Admin's current password.", example = "ChangeMe@12345")
        @NotBlank String password
) {
}
