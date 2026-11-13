package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * No password field: the account is created with a random, never-disclosed password and
 * {@code mustChangePassword = true}; an invite email lets the new staff member set their own via
 * {@code POST /api/v1/admin/auth/accept-invite}. No {@code superAdmin} field either - that stays
 * bootstrap-only and is never settable through this API.
 */
@Schema(description = "Creates a staff/admin account. No `password` field (a random, never-disclosed one is "
        + "generated and an invite email sent) and no `superAdmin` field (bootstrap-only, never settable here). "
        + "Profile/address/KYC/bank-detail rows are not created by this request - they start out empty and are "
        + "populated later, independently, via their own sub-resource endpoints.")
public record CreateAdminStaffRequest(
        @Schema(description = "Optional login username. An empty string (`\"\"`) is treated identically to omitting "
                + "it (normalized to `null` in storage), so two staff can both be created with no username without "
                + "colliding on the uniqueness check.", example = "new.staff", nullable = true)
        @Size(max = 100) String username,

        @Schema(description = "Login email - also where the invite email is sent. Must be unique (case-insensitive).",
                example = "new.staff@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Display name.", example = "New Staff Member", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 150) String fullName,

        @Schema(description = "Contact phone number, free-text/unvalidated format.", example = "+919876543210", nullable = true)
        @Size(max = 20) String phone,

        @Schema(description = "Role ids to assign (existing Roles > `id` values). Send `[]` for no roles yet - an "
                + "empty list is valid, `null` is not. Every id must already exist, and unless the caller is a "
                + "super-admin, the union of the requested roles' permissions must be entirely within the caller's "
                + "own current permissions (the same privilege-escalation rule as Roles > Create Role).",
                example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull List<UUID> roleIds
) {
}
