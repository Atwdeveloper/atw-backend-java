package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the faceauth module. Admin endpoints call this to enroll/view/remove a worker's face
 * template; a future {@code worker} module integration - explicitly deferred, see repo memory
 * "backend-folder-architecture" - will call {@link #hasEnrollment} to show an enrollment badge on the
 * worker's admin view, the same way {@code worker} already calls {@code KycService.getStatus}. An
 * interface from day one even though nothing calls it cross-module yet, per this codebase's standing
 * rule: declare a module's boundary before a caller exists, not after.
 */
public interface FaceAuthService {

    /** Never null - a not-enrolled placeholder ({@code enrolled=false}) if nothing is on file. */
    FaceEnrollmentDto getEnrollment(UUID workerId);

    boolean hasEnrollment(UUID workerId);

    /**
     * Batch lookup for a page of workers - backs the admin worker list's per-row face-enrollment
     * column without an N+1 Azure Blob SAS-URL call per row (mirrors {@code KycService.getStatuses}).
     * Workers with no enrollment at all are omitted from the map; callers should treat a missing entry
     * as "not enrolled". Deliberately just the enrollment timestamp, not the full
     * {@link FaceEnrollmentDto} - resolving the signed photo URL that carries requires a real external
     * call this method must never make once per row on a whole page (see
     * {@code WorkerSummaryResponse.faceEnrolledAt}'s Javadoc, and {@code hasProfilePhoto}'s identical
     * precedent: cheap data on the list, the signed URL resolved only on a single GET).
     */
    Map<UUID, Instant> getEnrollmentDates(Set<UUID> workerIds);

    /**
     * Enrolls (or fully re-enrolls, overwriting any existing template) a worker's face. Requires
     * explicit consent. Rejects the photo outright (throws, nothing persisted) if the face-auth
     * service itself flags it as poor quality or fails its liveness check - ATW never stores a
     * template the vendor itself considers unreliable.
     */
    FaceEnrollmentDto enroll(UUID workerId, String filename, String contentType, byte[] content,
                            boolean consentGiven, String consentIp);

    /** No-op if the worker has no enrollment. Also removes the stored enrollment photo. */
    void removeEnrollment(UUID workerId);

    /**
     * 1:N face identification - "whose face is this", as opposed to 1:1 "is this worker X" (not yet
     * built - nothing calls for it today). Compares the probe photo's embedding against every
     * currently-enrolled worker (brute-force cosine similarity in application code, same approach
     * legacy itself used - fine at today's scale; see repo memory for why pgvector/indexed search was
     * deferred rather than built up front).
     *
     * @throws BusinessRuleException if the probe photo itself fails quality/liveness (same checks as
     *         {@link #enroll}), or if nobody is enrolled yet at all - a genuinely different, actionable
     *         situation from "compared and found nothing". A confident-but-not-found result (nothing
     *         clears the threshold, or the top two candidates are too close to call) is NOT an
     *         exception - see {@link FaceIdentificationResult#matched()}.
     */
    FaceIdentificationResult identify(byte[] imageBytes, String contentType);
}
