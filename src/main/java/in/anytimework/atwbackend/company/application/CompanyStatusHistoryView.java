package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.shared.kernel.ActorRef;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a company's immutable status-transition audit trail - mirrors
 * {@code worker.application.WorkerStatusHistoryResponse}'s shape, returned by
 * {@code CompanyService.listStatusHistory}. {@code changedBy} is resolved from
 * {@code CompanyStatusHistory.changedByAdminId} (null means SYSTEM - no transition on this entity is
 * actually SYSTEM-triggered today, but the id can still be null if the acting admin's identity was
 * never captured) via {@code identity.application.lookup.AdminActorLookupService}, the same mechanism
 * {@code worker.application.WorkerStatusHistoryResponse} uses.
 */
public record CompanyStatusHistoryView(
        UUID id,
        CompanyStatus fromStatus,
        CompanyStatus toStatus,
        String reason,
        ActorRef changedBy,
        Instant changedAt
) {
}
