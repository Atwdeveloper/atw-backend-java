package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;

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
}
