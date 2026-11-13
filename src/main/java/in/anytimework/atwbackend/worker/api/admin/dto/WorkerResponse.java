package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full detail view - includes a resolved, short-lived profile photo URL (never returned on the list
 * view, see WorkerSummaryResponse). {@code faceEnrollment} is the same shape: the full
 * {@link FaceEnrollmentDto}, signed photo URL included, is cheap here because this method is called
 * at most once per request (never once per list row) - see {@code FaceAuthService.getEnrollmentDates}'s
 * Javadoc for why the list view instead gets only a timestamp.
 */
@Schema(description = "Full single-worker detail view returned by GET /{id} and by every write "
        + "endpoint on this controller that returns a worker.")
public record WorkerResponse(
        @Schema(example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e") UUID id,
        @Schema(description = "Generated at creation; never editable.", example = "WRK0000042") String workerCode,
        String firstName,
        String middleName,
        String lastName,
        @Schema(description = "Self-reported gender.") Gender gender,
        LocalDate dateOfBirth,
        String mobileNumber,
        String personalEmail,
        @Schema(description = "Id of the worker's department (Masterdata module).",
                example = "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d") UUID departmentId,
        @Schema(description = "Id of the worker's industry (Masterdata module), or null if not set.",
                example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", nullable = true) UUID industryId,
        @Schema(description = "Lifecycle status - see the status-change endpoints below for the full "
                + "transition rules.") WorkerStatus status,
        @Schema(description = "Free-text reason captured with the current status; only ever populated "
                + "when status is SUSPENDED, BLACKLISTED, or INACTIVE - null otherwise.", nullable = true)
        String statusReason,
        @Schema(description = "Short-lived (~15 minute) signed URL to the worker's uploaded profile "
                + "photo, or null if none has been uploaded. Re-fetch this endpoint to get a fresh URL "
                + "rather than caching it.", nullable = true) String profilePhotoUrl,
        @Schema(description = "The worker's current designations (1 to 3); exactly one has primary=true.")
        List<WorkerDesignationRef> designations,
        List<WorkerSkillRef> skills,
        @Schema(description = "Read-only summary resolved from the separate kyc module; not editable "
                + "through this controller.") KycStatus kycStatus,
        @Schema(description = "Read-only summary resolved from the separate faceauth module, including "
                + "a short-lived signed photo URL when enrolled; not editable through this controller. "
                + "FaceEnrollmentDto = {workerId, enrolled, enrolledAt, livenessConfidence, photoUrl}.",
                nullable = true) FaceEnrollmentDto faceEnrollment,
        Instant createdAt
) {
}
