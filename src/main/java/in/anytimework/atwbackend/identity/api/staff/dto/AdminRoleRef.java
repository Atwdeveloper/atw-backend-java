package in.anytimework.atwbackend.identity.api.staff.dto;

import java.util.UUID;

/** Minimal role reference embedded in {@link AdminStaffDto} - id + name only, not the full permission set. */
public record AdminRoleRef(UUID id, String name) {
}
