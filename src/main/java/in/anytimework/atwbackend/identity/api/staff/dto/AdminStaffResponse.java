package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Core staff/admin account record. Profile, address, KYC and bank-detail data are "
        + "deliberately not included here - fetch them via the sub-resource endpoints below.")
public record AdminStaffResponse(
        @Schema(description = "Staff/admin account id.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID id,

        @Schema(description = "Login username. `null` if this admin was created/left without one - `email` is "
                + "always a valid alternative login identifier.", example = "jdoe", nullable = true)
        String username,

        @Schema(description = "Login email, also where the invite/reset emails are sent. Always present; stored lowercased.",
                example = "new.staff@example.com")
        String email,

        @Schema(description = "Display name.", example = "New Staff Member")
        String fullName,

        @Schema(description = "Contact phone number, free-text/unvalidated format.", example = "+919876543210", nullable = true)
        String phone,

        @Schema(description = "True only for the single bootstrap super-admin account seeded at application startup. "
                + "Never settable through this API - a super-admin account also cannot be targeted by Update, Set "
                + "Status, or Delete Staff below (each rejects with `422 BUSINESS_RULE_VIOLATION`).", example = "false")
        boolean superAdmin,

        @Schema(description = "`ACTIVE` can authenticate; `DISABLED` cannot - every outstanding refresh token is "
                + "revoked the instant an account becomes `DISABLED`, and any access token still in use is rejected "
                + "on its very next request.", example = "ACTIVE")
        AdminStatus status,

        @Schema(description = "Every role currently assigned to this admin, sorted by name. Effective permissions "
                + "are the union of all assigned roles' permissions.")
        List<AdminRoleRef> roles,

        @Schema(description = "When this admin last completed a successful login. `null` if they have never logged in "
                + "(e.g. a newly-invited staff member who hasn't accepted their invite yet).",
                example = "2026-11-10T08:15:00Z", nullable = true)
        Instant lastLoginAt,

        @Schema(description = "When this account was created.", example = "2026-10-24T10:23:00Z")
        Instant createdAt
) {
}
