package in.anytimework.atwbackend.identity.api.admin.dto;

import java.util.List;

/** The permission catalog grouped by module, for building the role editor matrix. */
public record PermissionGroup(String module, List<PermissionItem> permissions) {

    public record PermissionItem(String code, String action, String label) {
    }
}
