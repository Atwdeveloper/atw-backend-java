package in.anytimework.atwbackend.identity.api.staff.dto;

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
public record CreateAdminStaffRequest(
        @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone,
        @NotNull List<UUID> roleIds
) {
}
