package in.anytimework.atwbackend.identity.api.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAdminRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String description,
        @NotNull List<@NotBlank String> permissionCodes
) {
}
