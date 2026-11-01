package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import jakarta.validation.constraints.NotNull;

/** Shared status-update body for department/industry/designation/skill - an explicit target, not a blind toggle. */
public record UpdateActiveStatusRequest(@NotNull ActiveStatus status) {
}
