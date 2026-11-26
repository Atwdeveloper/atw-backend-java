package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.shared.kernel.ActorRef;

import java.time.Instant;
import java.util.UUID;

/** One row of a company's immutable status-transition audit trail - mirrors worker.api.admin.dto.WorkerStatusHistoryResponse's shape, including resolving changedBy to an ActorRef (id, display name, userType) rather than a raw id or email. */
public record CompanyStatusHistoryResponse(
        UUID id,
        CompanyStatus fromStatus,
        CompanyStatus toStatus,
        String reason,
        ActorRef changedBy,
        Instant changedAt
) {
}
