package in.anytimework.atwbackend.worker.domain;

/**
 * Lifecycle state of a worker's platform record.
 *
 * <pre>
 * REGISTERED -&gt; KYC_PENDING -&gt; ACTIVE &lt;-&gt; INACTIVE
 *                                  |
 *                                  +-&gt; SUSPENDED -&gt; ACTIVE
 *                                  |
 *                                  +-&gt; BLACKLISTED (terminal in practice; see WorkerService)
 * </pre>
 *
 * Distinguishes a temporary, no-fault pause (INACTIVE - e.g. "on leave", "left voluntarily") from a
 * punitive hold pending investigation (SUSPENDED) from a permanent ban (BLACKLISTED) - legacy
 * conflated all three into a single inactive flag with no reason captured. See
 * {@code worker_status_history} for the transition audit trail.
 */
public enum WorkerStatus {
    REGISTERED,
    KYC_PENDING,
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    BLACKLISTED
}
