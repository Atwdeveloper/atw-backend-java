package in.anytimework.atwbackend.identity.api.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Changes the signed-in admin's own password. Revokes every refresh token for "
        + "every device/session on success.")
public record ChangePasswordRequest(
        @Schema(description = "The account's current password, for verification.", example = "ChangeMe@12345")
        @NotBlank String currentPassword,

        @Schema(description = "The new password.", example = "NewStrongPassword@123")
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
