package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Bulk counterpart to {@link UpdateWorkerStatusRequest} - same conditional reason requirement, enforced per item in WorkerService. */
@Schema(description = "Applies the same guarded status transition as the single-worker status endpoint "
        + "to every id independently - one id failing its guard does not roll back the others; see the "
        + "returned BulkActionResult for which ids failed and why.")
public record BulkUpdateWorkerStatusRequest(
        @Schema(description = "Worker ids to update.",
                example = "[\"c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e\"]") @NotEmpty Set<UUID> ids,
        @Schema(description = "Target status. REGISTERED/KYC_PENDING can never be set here (reached only "
                + "automatically); ACTIVE can only be set here from SUSPENDED/INACTIVE, never from "
                + "REGISTERED/KYC_PENDING (that requires completing KYC). Entering or leaving BLACKLISTED "
                + "additionally requires the caller to be a super-admin.", example = "SUSPENDED")
        @NotNull WorkerStatus status,
        @Schema(description = "Required when status is SUSPENDED, BLACKLISTED, or INACTIVE; ignored "
                + "otherwise.", nullable = true, example = "Failed a random KYC re-verification check")
        @Size(max = 500) String reason
) {
}
