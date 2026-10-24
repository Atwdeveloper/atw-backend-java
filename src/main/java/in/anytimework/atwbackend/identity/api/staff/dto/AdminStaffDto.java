package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.AdminStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminStaffDto(
        UUID id,
        String username,
        String email,
        String fullName,
        String phone,
        boolean superAdmin,
        AdminStatus status,
        List<AdminRoleRef> roles,
        Instant lastLoginAt,
        Instant createdAt
) {
}
