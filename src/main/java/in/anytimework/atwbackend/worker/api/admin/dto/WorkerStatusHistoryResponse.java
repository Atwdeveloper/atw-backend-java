package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One immutable status transition row, newest first. Never updated after "
        + "creation.")
public record WorkerStatusHistoryResponse(
        @Schema(example = "b5c6d7e8-f9a0-4b2c-9d3e-4f5a6b7c8d9e") UUID id,
        @Schema(description = "Null only for the very first row, recorded at worker creation "
                + "(REGISTERED has no prior status).", nullable = true) WorkerStatus fromStatus,
        WorkerStatus toStatus,
        @Schema(description = "Captured whenever the target status is SUSPENDED, BLACKLISTED, or "
                + "INACTIVE; null for every other transition.", nullable = true) String reason,
        @Schema(description = "Username of the admin who made the change, or \"system\" for the one "
                + "transition this endpoint never makes directly - the automatic REGISTERED/KYC_PENDING "
                + "to ACTIVE move triggered by KYC verification, which runs asynchronously outside any "
                + "admin's own request.", example = "admin@anytimework.in") String changedBy,
        Instant changedAt
) {
}
