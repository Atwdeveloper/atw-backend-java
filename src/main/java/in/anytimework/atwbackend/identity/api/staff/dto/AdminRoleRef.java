package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Minimal role reference embedded in {@link AdminStaffResponse} - id + name only, not the full permission set. */
@Schema(description = "Minimal role reference - id + name only. Fetch Roles > Get Role for its full permission set.")
public record AdminRoleRef(
        @Schema(description = "Role id.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,
        @Schema(description = "Role name at the time of this response.", example = "Senior Developer")
        String name
) {
}
