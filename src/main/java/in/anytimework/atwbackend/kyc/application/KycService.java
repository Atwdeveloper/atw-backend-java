package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.kyc.application.dto.KycProfileDto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the kyc module. The {@code worker} module calls this by worker id and never touches
 * {@code worker_kyc_profile} directly; the admin KYC endpoints ({@code kyc.api.admin}) call it too,
 * so this is a Spring interface rather than a concrete class (per the project's "service is an
 * interface only when another module calls it" rule).
 */
public interface KycService {

    /** Current profile. Returns a NOT_STARTED placeholder if nothing has been submitted yet - never null. */
    KycProfileDto getProfile(UUID workerId);

    /** Manual, unverified entry (no live proof) - moves status to PENDING if not already further along. */
    KycProfileDto submitAadhaar(UUID workerId, String aadhaarNumber);

    KycProfileDto submitPan(UUID workerId, String panNumber);

    /**
     * Completes a worker's KYC from an already-successful Aadhaar e-KYC verification (see
     * {@code WorkerIntakeService}), called by {@code WorkerService.create()} in the same transaction
     * as creating the worker. Marks the verification consumed so it cannot be reused for a second
     * worker. Publishes {@link KycVerifiedEvent} (worker auto-activates) since this always results in
     * VERIFIED - there is no partial/pending outcome from a successful live verification.
     */
    KycVerificationCompletionResult completeFromVerification(UUID workerId, UUID verificationId);

    /** NOT_STARTED if the worker has no KYC profile at all yet. */
    KycStatus getStatus(UUID workerId);

    /** Worker ids currently in any of the given aggregate KYC states - backs the admin worker list's (multiselect) KYC-status filter. */
    Set<UUID> findWorkerIdsByStatus(Set<KycStatus> statuses);

    /**
     * Batch status lookup for a page of workers - backs the admin worker list's per-row KYC status
     * column without an N+1 call per row. Workers with no KYC profile at all are omitted; callers
     * should default a missing entry to NOT_STARTED.
     */
    Map<UUID, KycStatus> getStatuses(Set<UUID> workerIds);
}
