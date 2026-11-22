package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Explicit target status, never a blind toggle - same reasoning as worker/designation status endpoints. */
@Schema(description = """
        Allowed transitions: PENDING_VERIFICATION <-> REJECTED, PENDING_VERIFICATION -> ACTIVE, \
        ACTIVE <-> SUSPENDED - every other pair (including REJECTED -> ACTIVE directly, ACTIVE -> \
        REJECTED, and setting the status already in effect) is rejected. `reason` is required when the \
        target is SUSPENDED or REJECTED, optional otherwise.""")
public record UpdateCompanyStatusRequest(
        @NotNull CompanyStatus status,
        @Schema(example = "GST number could not be verified after 2 attempts", nullable = true) @Size(max = 500) String reason
) {
}
