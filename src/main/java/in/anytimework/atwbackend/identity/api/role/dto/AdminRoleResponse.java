package in.anytimework.atwbackend.identity.api.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "A role and the permission set it currently grants.")
public record AdminRoleResponse(
        @Schema(description = "Role id.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Unique role name.", example = "QA Tester")
        String name,

        @Schema(description = "Free-text description, if one was set.", example = "Read-only access for QA", nullable = true)
        String description,

        @Schema(description = "When `true`, this role is protected from Update and Delete (`422 BUSINESS_RULE_VIOLATION`) - "
                + "it can still be assigned to or removed from staff normally. This flag can only ever be set by seed data; "
                + "no endpoint in this API (including Create Role) can create or promote a role to `true`.",
                example = "false")
        boolean systemRole,

        @Schema(description = "Every permission code this role currently grants, sorted alphabetically.",
                example = "[\"admin_staff.view\", \"dashboard.view\"]")
        List<String> permissionCodes
) {
}
