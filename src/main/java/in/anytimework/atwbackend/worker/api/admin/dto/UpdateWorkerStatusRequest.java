package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An explicit target status, never a blind toggle. reason is validated as required for SUSPENDED/BLACKLISTED/INACTIVE in WorkerService. */
@Schema(description = "An explicit target status, never a blind toggle (so two quick calls can't race "
        + "each other into an unpredictable end state). REGISTERED and KYC_PENDING can never be set "
        + "here - they're reached only automatically. ACTIVE can only be reached here from SUSPENDED or "
        + "INACTIVE - from REGISTERED/KYC_PENDING, a worker becomes ACTIVE only by completing KYC "
        + "verification. Setting the same status the worker is already in is rejected. Entering or "
        + "leaving BLACKLISTED additionally requires the caller to be a super-admin.")
public record UpdateWorkerStatusRequest(
        @Schema(example = "SUSPENDED") @NotNull WorkerStatus status,
        @Schema(description = "Required when status is SUSPENDED, BLACKLISTED, or INACTIVE; ignored "
                + "(may be omitted) otherwise.", nullable = true, example = "On unapproved leave since 2026-11-01")
        @Size(max = 500) String reason
) {
}
