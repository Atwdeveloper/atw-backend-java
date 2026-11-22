package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of a company's immutable status-transition audit trail - mirrors worker.api.admin.dto.WorkerStatusHistoryResponse's shape. changedBy is the acting admin's email snapshot at the time of the change (see CompanyStatusHistory's Javadoc), or null for the first row recorded at creation. */
public record CompanyStatusHistoryResponse(
        UUID id,
        CompanyStatus fromStatus,
        CompanyStatus toStatus,
        String reason,
        String changedBy,
        Instant changedAt
) {
}
