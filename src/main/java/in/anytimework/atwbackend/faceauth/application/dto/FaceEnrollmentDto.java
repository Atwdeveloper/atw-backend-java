package in.anytimework.atwbackend.faceauth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A worker's current face-enrollment status. A worker who has never enrolled still gets "
        + "this exact shape back, not a 404 - with `enrolled=false` and every other field `null`.")
public record FaceEnrollmentDto(
        @Schema(description = "The worker this status is for.")
        UUID workerId,
        @Schema(description = "Whether this worker currently has a face template on file. When `false`, every "
                + "field below is `null`.")
        boolean enrolled,
        @Schema(description = "When the current template was (re-)enrolled. Re-enrollment overwrites this - it "
                + "is never a history of past enrollments, only ever the latest one. `null` when `enrolled` is "
                + "`false`.", example = "2026-11-10T09:15:00Z")
        Instant enrolledAt,
        @Schema(description = "The face-auth provider's liveness confidence for the photo that was accepted at "
                + "enrollment time (higher is more confident; the accept/reject decision itself already "
                + "happened on the provider's side before this row was ever written). `null` when `enrolled` "
                + "is `false`.", example = "0.97")
        Double livenessConfidence,
        @Schema(description = "Short-lived (about 15 minutes) signed URL to the enrollment reference photo. "
                + "Call this endpoint again for a fresh one once it expires - never cache this value. `null` "
                + "when `enrolled` is `false`.",
                example = "https://atwstorage.blob.core.windows.net/documents/worker_face_enrollment_photo/"
                        + "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b/7f3e2d1c-0b9a-4f8e-9d7c-6b5a4f3e2d1c.jpg"
                        + "?sv=2025-05-05&se=2026-11-10T09%3A30%3A00Z&sr=b&sp=r&sig=REDACTED")
        String photoUrl
) {
}
