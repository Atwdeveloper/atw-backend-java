package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the paginated admin company list - deliberately slim (no owner, no addresses), mirroring CompanySummaryView; see CompanyService's Javadoc for why list() never pays a per-row N+1 cost. */
public record CompanySummaryResponse(
        UUID id,
        String companyCode,
        String name,
        UUID industryId,
        CompanyStatus status,
        Instant createdAt
) {
}
