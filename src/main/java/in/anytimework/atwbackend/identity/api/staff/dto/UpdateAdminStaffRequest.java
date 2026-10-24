package in.anytimework.atwbackend.identity.api.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Email is intentionally not editable here - changing a login identifier is a bigger decision than a profile edit. */
public record UpdateAdminStaffRequest(
        @Size(max = 100) String username,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone,
        @NotNull List<UUID> roleIds
) {
}
