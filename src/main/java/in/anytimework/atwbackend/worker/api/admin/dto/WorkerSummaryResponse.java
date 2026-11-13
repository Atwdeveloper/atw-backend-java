package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Lighter list-row view - no signed photo URL (resolved only on single-GET, never on a list - see
 * FRONTEND_HANDOVER.md's file-upload convention). {@code faceEnrolledAt} follows the exact same rule:
 * {@code null} means not enrolled, a non-null timestamp means enrolled as of that instant - never the
 * signed enrollment-photo URL itself (see {@code FaceAuthService.getEnrollmentDates}'s Javadoc for why
 * that would be an N+1 Azure Blob call per row).
 */
@Schema(description = "One row of the worker list/export. Lighter than WorkerResponse: no signed "
        + "photo URL, no full faceEnrollment object, no address/bank detail.")
public record WorkerSummaryResponse(
        @Schema(example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e") UUID id,
        @Schema(example = "WRK0000042") String workerCode,
        String firstName,
        String lastName,
        String mobileNumber,
        @Schema(description = "Display name of the worker's primary designation, or null if none is "
                + "assigned yet. Batch-resolved for display only - filter/sort by designationId, not by "
                + "this name.", nullable = true, example = "Mason") String primaryDesignationName,
        @Schema(description = "One of the sortable/filterable status values.") WorkerStatus status,
        @Schema(description = "Read-only summary resolved from the separate kyc module.") KycStatus kycStatus,
        @Schema(description = "Whether a profile photo has been uploaded. The signed URL itself is "
                + "only ever returned by GET /{id}, never in this list view.") boolean hasProfilePhoto,
        @Schema(description = "Null = not enrolled in face biometric auth. A non-null value is the "
                + "instant enrollment completed - never the signed enrollment photo itself (see "
                + "WorkerResponse.faceEnrollment for that).", nullable = true) Instant faceEnrolledAt
) {
}
