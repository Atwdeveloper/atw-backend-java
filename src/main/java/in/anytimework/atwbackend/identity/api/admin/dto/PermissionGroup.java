package in.anytimework.atwbackend.identity.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The permission catalog grouped by module, for building the role editor matrix. */
@Schema(description = "One module's worth of permission codes, e.g. all of `admin_staff.*`.")
public record PermissionGroup(
        @Schema(description = "Module name - the part of every `code` below before the dot.", example = "admin_staff")
        String module,
        @Schema(description = "Every permission code defined for this module, ordered by action.")
        List<PermissionItem> permissions
) {

    @Schema(description = "A single assignable permission.")
    public record PermissionItem(
            @Schema(description = "Exact string to send in `permissionCodes[]` on Create/Update Role, or to match against an admin's `authorities[]`. Always `<module>.<action>`.",
                    example = "admin_staff.view_kyc")
            String code,
            @Schema(description = "The verb suffix of `code` on its own.", example = "view_kyc")
            String action,
            @Schema(description = "Display label for a permission-matrix checkbox. Currently always identical to `action` - kept as a separate field so the catalog can later diverge without an API shape change.",
                    example = "view_kyc")
            String label
    ) {
    }
}
