package in.anytimework.atwbackend.identity.api.role.dto;

import java.util.List;
import java.util.UUID;

public record AdminRoleDto(
        UUID id,
        String name,
        String description,
        boolean systemRole,
        List<String> permissionCodes
) {
}
