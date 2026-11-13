package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Shared status-update body for department/industry/designation/skill - an explicit target, not a blind toggle. */
public record UpdateActiveStatusRequest(
        @Schema(description = "Target status to set. Always an explicit value, never a blind toggle, so two "
                + "concurrent calls cannot race into an unpredictable end state.", example = "INACTIVE")
        @NotNull ActiveStatus status
) {
}
