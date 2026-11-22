package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Sets a staff member's account status to an explicit target rather than toggling it, so two "
        + "quick successive calls can't race into an unintended end state.")
public record UpdateAdminStaffStatusRequest(
        @Schema(description = "Target status. Setting `DISABLED` immediately revokes every refresh token for that "
                + "admin and rejects any access token still in use on its very next authenticated request - there "
                + "is no lag waiting for the current token to expire. Rejected with `422 BUSINESS_RULE_VIOLATION` if "
                + "the target is your own account or a super-admin account.",
                example = "DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull AdminStatus status
) {
}
