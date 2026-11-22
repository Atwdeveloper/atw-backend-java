package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the paginated admin company list - deliberately slim: every field is a plain scalar
 * column straight off {@code Company}, needing zero extra per-row queries (no owner lookup, no
 * address lookup) so {@code CompanyService.list} never pays an N+1 cost across a page. See
 * {@link CompanyView}'s Javadoc for the richer, single-record counterpart.
 */
public record CompanySummaryView(UUID id, String companyCode, String name, UUID industryId, CompanyStatus status,
                                  Instant createdAt) {
}
