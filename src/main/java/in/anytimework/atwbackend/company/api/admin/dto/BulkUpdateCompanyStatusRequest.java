package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Bulk counterpart to {@link UpdateCompanyStatusRequest} - same conditional reason requirement, enforced per item in CompanyService. */
@Schema(description = "Applies the same guarded status transition as the single-company status endpoint "
        + "to every id independently - one id failing its guard does not roll back the others; see the "
        + "returned BulkActionResult for which ids failed and why.")
public record BulkUpdateCompanyStatusRequest(
        @Schema(description = "Company ids to update.",
                example = "[\"c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e\"]") @NotEmpty Set<UUID> ids,
        @Schema(description = "Target status. Allowed transitions: PENDING_VERIFICATION <-> REJECTED, "
                + "PENDING_VERIFICATION -> ACTIVE, ACTIVE <-> SUSPENDED - every other pair (including "
                + "REJECTED -> ACTIVE directly, ACTIVE -> REJECTED, and setting the status already in "
                + "effect) is rejected per id.", example = "SUSPENDED")
        @NotNull CompanyStatus status,
        @Schema(description = "Required when status is SUSPENDED or REJECTED; ignored otherwise.",
                nullable = true, example = "Non-payment") @Size(max = 500) String reason
) {
}
