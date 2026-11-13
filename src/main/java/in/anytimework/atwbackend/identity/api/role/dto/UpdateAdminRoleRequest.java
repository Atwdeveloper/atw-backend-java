package in.anytimework.atwbackend.identity.api.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Full replace of a role's name, description, and entire permission set - omitted codes are "
        + "removed from the role, this is not a merge/patch. Rejected with 422 if the target role is a system role.")
public record UpdateAdminRoleRequest(
        @Schema(description = "Unique role name (must not collide with a *different* role).", example = "QA Tester",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Free-text description. Optional.", example = "Updated description", nullable = true)
        @Size(max = 300) String description,

        @Schema(description = "Complete replacement permission set - same validation and privilege-escalation rule as "
                + "Create Role. Send every code the role should end up with, not just the ones changing.",
                example = "[\"dashboard.view\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull List<@NotBlank String> permissionCodes
) {
}
