package in.anytimework.atwbackend.kyc.application;

import java.util.UUID;

/**
 * Published when a worker's aggregate KYC status first transitions to VERIFIED (not on every
 * recompute while it stays verified). Persisted by Spring Modulith's event publication registry
 * (the transactional outbox, already wired via spring-modulith-starter-jdbc), so a worker's
 * auto-activation is never lost even if the process crashes before the listener runs - same
 * reliability shape as {@code notification}'s {@code EmailRequestedEvent}.
 */
public record KycVerifiedEvent(UUID workerId) {
}
