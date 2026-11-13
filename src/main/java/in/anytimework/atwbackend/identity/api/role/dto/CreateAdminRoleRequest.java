package in.anytimework.atwbackend.identity.api.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Creates a new, non-system role (`systemRole` always starts `false` - only seed data can ever carry `true`).")
public record CreateAdminRoleRequest(
        @Schema(description = "Unique role name.", example = "QA Tester", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Free-text description shown in the role editor. Optional.", example = "Read-only access for QA",
                nullable = true)
        @Size(max = 300) String description,

        @Schema(description = "Permission codes this role grants - see `GET /api/v1/admin/permissions` for the valid set. "
                + "Send `[]` for a role with no permissions yet; an empty list is valid, `null` is not. Every entry must "
                + "already exist in the catalog, and the whole set must be within the caller's own current permissions "
                + "unless the caller is a super-admin.",
                example = "[\"dashboard.view\", \"workers.view\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull List<@NotBlank String> permissionCodes
) {
}
