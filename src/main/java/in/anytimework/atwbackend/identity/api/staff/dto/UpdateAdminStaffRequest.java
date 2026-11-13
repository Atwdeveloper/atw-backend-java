package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Email is intentionally not editable here - changing a login identifier is a bigger decision than a profile edit. */
@Schema(description = "Full replace of username/fullName/phone/roles for an existing staff member. `email` is "
        + "intentionally not editable here - not currently exposed through any endpoint. Rejected with "
        + "`422 BUSINESS_RULE_VIOLATION` if the target is a super-admin account.")
public record UpdateAdminStaffRequest(
        @Schema(description = "Optional login username. An empty string is treated identically to omitting it.",
                example = "jdoe", nullable = true)
        @Size(max = 100) String username,

        @Schema(description = "Display name.", example = "Updated Name", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 150) String fullName,

        @Schema(description = "Contact phone number, free-text/unvalidated format.", example = "+919876543210", nullable = true)
        @Size(max = 20) String phone,

        @Schema(description = "Complete replacement role-id set (existing Roles > `id` values) - same validation and "
                + "privilege-escalation rule as Create Staff. If the resulting set differs from what the staff member "
                + "had before, their `security_version` is bumped and every refresh token they hold is revoked, "
                + "forcing a fresh login so a permission *removal* takes effect immediately instead of riding out "
                + "the access token's remaining TTL. A plain name/phone edit with the same roles does not trigger this.",
                example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull List<UUID> roleIds
) {
}
