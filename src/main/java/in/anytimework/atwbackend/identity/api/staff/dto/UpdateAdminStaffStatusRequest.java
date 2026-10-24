package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.AdminStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAdminStaffStatusRequest(
        @NotNull AdminStatus status
) {
}
