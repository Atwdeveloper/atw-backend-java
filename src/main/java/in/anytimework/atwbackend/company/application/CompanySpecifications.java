package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.Company;
import in.anytimework.atwbackend.company.domain.CompanyAddress;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic multi-field filtering for the admin company list - same {@link Specification}-per-filter
 * pattern as {@code worker.application.WorkerSpecifications}/{@code identity.application.AdminStaffSpecifications}.
 * Most filters here are a direct column on {@link Company}; {@link #hasState}/{@link #hasCity} are the
 * exception, using the same subquery-against-a-satellite-table technique as
 * {@code worker.application.WorkerSpecifications.hasState}/{@code hasCity} (the template this mirrors).
 * Composed and combined by {@code CompanyService} via
 * {@code shared.persistence.Specifications.allOf} - never via {@code Specification.where(null).and(...)},
 * which throws on this Spring Data version.
 */
final class CompanySpecifications {

    private CompanySpecifications() {
    }

    static Specification<Company> hasStatus(Set<CompanyStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Company> hasIndustry(Set<UUID> industryIds) {
        return (root, query, cb) -> root.get("industryId").in(industryIds);
    }

    static Specification<Company> matchesSearch(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("companyCode")), pattern)
        );
    }

    /** {@code true} -> verified (gstVerifiedAt set), {@code false} -> unverified (still null). */
    static Specification<Company> isGstVerified(boolean verified) {
        return (root, query, cb) -> verified ? cb.isNotNull(root.get("gstVerifiedAt")) : cb.isNull(root.get("gstVerifiedAt"));
    }

    /**
     * Filters on {@code createdAt}. Interprets both bounds as whole calendar days in UTC: {@code from}
     * is midnight UTC that day (inclusive), {@code to} is midnight UTC the following day (exclusive) -
     * same semantics as {@code worker.application.WorkerSpecifications.registeredBetween} - either
     * bound may be null on its own, but this is only ever called when at least one is non-null.
     */
    static Specification<Company> createdBetween(LocalDate from, LocalDate to) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return (root, query, cb) -> {
            if (fromInstant != null && toInstant != null) {
                return cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant),
                        cb.lessThan(root.get("createdAt"), toInstant));
            }
            return fromInstant != null
                    ? cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant)
                    : cb.lessThan(root.get("createdAt"), toInstant);
        };
    }

    /**
     * Matches a company with ANY {@link CompanyAddress} referencing one of the given state ids - unlike
     * {@code worker.application.WorkerSpecifications.hasState}, there is no address-type distinction to
     * restrict to (a company's addresses carry no PERMANENT/CURRENT split - see {@link CompanyAddress}'s
     * Javadoc). Same subquery technique: {@link CompanyAddress} holds no navigable association back to
     * {@link Company}.
     */
    static Specification<Company> hasState(Set<UUID> stateIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var companyAddress = subquery.from(CompanyAddress.class);
            subquery.select(companyAddress.get("companyId"))
                    .where(companyAddress.get("address").get("stateId").in(stateIds));
            return root.get("id").in(subquery);
        };
    }

    /** City counterpart of {@link #hasState} - same either-address matching (there is no address-type distinction for Company). */
    static Specification<Company> hasCity(Set<UUID> cityIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var companyAddress = subquery.from(CompanyAddress.class);
            subquery.select(companyAddress.get("companyId"))
                    .where(companyAddress.get("address").get("cityId").in(cityIds));
            return root.get("id").in(subquery);
        };
    }
}
