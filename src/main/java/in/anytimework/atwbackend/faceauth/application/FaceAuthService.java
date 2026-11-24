package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the faceauth module. Admin endpoints call this to enroll/view/remove a worker's face
 * template and run 1:N identify. {@code worker.application.WorkerService} also calls
 * {@link #getEnrollment}/{@link #getEnrollmentDates} (built 2026-11-13) to show an enrollment badge on
 * a worker's admin view/listing, the same way it already calls {@code KycService.getStatus} - this is
 * the one and only direction a dependency between {@code worker} and this module may ever go, since
 * Spring Modulith's own structural check (see {@link #excludeFromMatching}'s Javadoc) rejects a cycle;
 * {@link #excludeFromMatching} is specifically shaped to let {@code worker} push information this
 * module needs without ever reversing that direction.
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
     * Every worker id with an enrollment row on file - regardless of
     * {@link #excludeFromMatching}'s exclusion flag (that answers a different question: "still an
     * eligible identify/duplicate-check candidate", not "has a row at all"). Backs the admin worker
     * list's {@code faceEnrolled} filter, the same "ask the owning module for an id-set, then filter
     * by it" technique {@code worker.application.KycService.findWorkerIdsByStatus} already established
     * for the {@code kycStatus} filter - never a cross-module JPA join.
     */
    Set<UUID> findEnrolledWorkerIds();

    /**
     * Enrolls (or fully re-enrolls, overwriting any existing template) a worker's face. Requires
     * explicit consent. Rejects the photo outright (throws, nothing persisted) if the face-auth
     * service itself flags it as poor quality or fails its liveness check - ATW never stores a
     * template the vendor itself considers unreliable.
     *
     * <p>Also rejects outright (throws {@link ConflictException}, nothing persisted) if the new photo
     * confidently matches a DIFFERENT already-enrolled, not-excluded worker - same unconditional
     * every-enroll duplicate-face check as legacy's {@code FaceAuthService::findDuplicateFace}. See
     * {@link #excludeFromMatching}'s Javadoc for what "excluded" means here.</p>
     */
    FaceEnrollmentDto enroll(UUID workerId, String filename, String contentType, byte[] content,
                            boolean consentGiven, String consentIp);

    /** No-op if the worker has no enrollment. Also removes the stored enrollment photo. */
    void removeEnrollment(UUID workerId);

    /**
     * 1:N face identification - "whose face is this", as opposed to 1:1 "is this worker X" (not yet
     * built - nothing calls for it today). Compares the probe photo's embedding against every
     * enrolled, not-excluded worker (brute-force cosine similarity in application code, same approach
     * legacy itself used - fine at today's scale; see repo memory for why pgvector/indexed search was
     * deferred rather than built up front). See {@link #excludeFromMatching}'s Javadoc for what
     * "excluded" means and why it works this way rather than this module asking {@code worker} for
     * the eligible set at call time.
     *
     * @throws BusinessRuleException if the probe photo itself fails quality/liveness (same checks as
     *         {@link #enroll}), or if nobody eligible is enrolled yet at all - a genuinely different,
     *         actionable situation from "compared and found nothing". A confident-but-not-found result
     *         (nothing clears the threshold, or the top two candidates are too close to call) is NOT
     *         an exception - see {@link FaceIdentificationResult#matched()}.
     */
    FaceIdentificationResult identify(byte[] imageBytes, String contentType);

    /**
     * Marks this worker's face template (if any; a no-op otherwise) as permanently excluded from
     * {@link #identify} and {@link #enroll}'s duplicate-face check - it can never again come up as a
     * match, though the row itself is never deleted (preserved for audit/reconciliation, same as
     * every other worker satellite table on a soft-delete - see {@code V27__worker_soft_delete.sql}).
     *
     * <p>Called by {@code worker.application.WorkerService.delete} the moment a worker is
     * soft-deleted - this is how {@code faceauth} learns a worker has become ineligible, rather than
     * {@code identify}/{@code enroll} asking {@code worker} "who's still eligible" at match time.
     * {@code worker} already depends on this module ({@code WorkerService} calls {@link #getEnrollment}/
     * {@link #getEnrollmentDates} for its own display purposes), so this module calling back into
     * {@code worker.application.WorkerService} would be a circular MODULE dependency - caught for real
     * by {@code ModularityTests.verifiesModularStructure} (Spring Modulith's own structural check,
     * which flags a cycle at the package/module level regardless of which specific classes within each
     * module are involved - a worker-side call into THIS module's own already-published interface is
     * the one direction that was always safe). There is deliberately no symmetric "include again"
     * method - workers have no restore/undelete endpoint yet either (see {@code WorkerService.delete}'s
     * own Javadoc); add one here the same day a restore endpoint is ever built, not before.</p>
     */
    void excludeFromMatching(UUID workerId);
}
