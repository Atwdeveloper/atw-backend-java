package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a company's immutable status-transition audit trail - mirrors
 * {@code worker.application.WorkerStatusHistoryResponse}'s shape, returned by
 * {@code CompanyService.listStatusHistory}. Sourced from {@code CompanyStatusHistory.changedByAdminEmail}
 * (the admin-email snapshot taken at write time) rather than the generic {@code BaseEntity.createdBy}
 * audit column, since that snapshot is exactly what this entity was built to preserve - see
 * {@code CompanyStatusHistory}'s own Javadoc.
 */
public record CompanyStatusHistoryView(
        UUID id,
        CompanyStatus fromStatus,
        CompanyStatus toStatus,
        String reason,
        String changedByAdminEmail,
        Instant changedAt
) {
}
