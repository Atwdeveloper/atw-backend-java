package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerAddress;
import in.anytimework.atwbackend.worker.domain.WorkerDesignation;
import in.anytimework.atwbackend.worker.domain.WorkerSkill;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic multi-field filtering for the admin worker list - no precedent for this existed yet
 * anywhere in the codebase (the only other admin list, AdminStaff, does a plain unfiltered
 * {@code findAll(pageable)}), so this establishes the pattern: {@link Specification}, already part
 * of {@code spring-data-jpa} (no new dependency), composed per non-empty filter. Designation/skill
 * filters use a subquery against the join tables rather than a JPA relationship, consistent with
 * {@link WorkerDesignation}/{@link WorkerSkill} holding no navigable association back to
 * {@link Worker}.
 *
 * <p>Every filter is multiselect: each method takes a {@link Set} and matches ANY value in it via
 * {@code Path.in(Collection)} - same mechanism {@link #idIn} already used for a single-valued
 * cross-module id lookup, now applied uniformly to every column/subquery filter too.</p>
 */
final class WorkerSpecifications {

    private WorkerSpecifications() {
    }

    static Specification<Worker> hasStatus(Set<WorkerStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Worker> hasIndustry(Set<UUID> industryIds) {
        return (root, query, cb) -> root.get("industryId").in(industryIds);
    }

    static Specification<Worker> hasDesignation(Set<UUID> designationIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var workerDesignation = subquery.from(WorkerDesignation.class);
            subquery.select(workerDesignation.get("workerId"))
                    .where(workerDesignation.get("designationId").in(designationIds));
            return root.get("id").in(subquery);
        };
    }

    static Specification<Worker> hasSkill(Set<UUID> skillIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var workerSkill = subquery.from(WorkerSkill.class);
            subquery.select(workerSkill.get("workerId")).where(workerSkill.get("skillId").in(skillIds));
            return root.get("id").in(subquery);
        };
    }

    /** Already-resolved worker ids (e.g. from a cross-module KYC-status lookup) - never queried via a JPA join across module boundaries. */
    static Specification<Worker> idIn(Set<UUID> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    /**
     * Negation of {@link #idIn} (e.g. "not face-enrolled"). Callers must never invoke this with an
     * empty {@code ids} set and expect it to mean "matches everyone" - {@code Path.in(emptyCollection)}
     * is the same invalid-SQL hazard {@link #idIn} already has to be guarded against by its own
     * callers; when {@code ids} is empty, skip adding this predicate at all instead.
     */
    static Specification<Worker> idNotIn(Set<UUID> ids) {
        return (root, query, cb) -> cb.not(root.get("id").in(ids));
    }

    /**
     * Matches a worker whose PERMANENT or CURRENT {@link WorkerAddress} has one of the given state
     * ids - deliberately not restricted to one address type, see {@link WorkerSearchCriteria}'s
     * Javadoc. Same subquery technique as {@link #hasDesignation}/{@link #hasSkill}:
     * {@link WorkerAddress} holds no navigable association back to {@link Worker}.
     */
    static Specification<Worker> hasState(Set<UUID> stateIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var workerAddress = subquery.from(WorkerAddress.class);
            subquery.select(workerAddress.get("workerId"))
                    .where(workerAddress.get("address").get("stateId").in(stateIds));
            return root.get("id").in(subquery);
        };
    }

    /** City counterpart of {@link #hasState} - same either-address-type matching. */
    static Specification<Worker> hasCity(Set<UUID> cityIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var workerAddress = subquery.from(WorkerAddress.class);
            subquery.select(workerAddress.get("workerId"))
                    .where(workerAddress.get("address").get("cityId").in(cityIds));
            return root.get("id").in(subquery);
        };
    }

    /**
     * Filters on {@code createdAt} (see {@link WorkerSearchCriteria}'s Javadoc for why this - not a
     * separate column - is "registration date"). Interprets both bounds as whole calendar days in
     * UTC: {@code from} is midnight UTC that day (inclusive), {@code to} is midnight UTC the
     * following day (exclusive) - either bound may be null on its own, but this is only ever called
     * when at least one is non-null.
     */
    static Specification<Worker> registeredBetween(LocalDate from, LocalDate to) {
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

    static Specification<Worker> matchesSearch(String term) {
        String pattern = "%" + term.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(root.get("mobileNumber")), pattern),
                cb.like(cb.lower(root.get("workerCode")), pattern),
                // personalEmail is nullable - cb.lower(null column) is still valid SQL (yields NULL,
                // which LIKE never matches), so no extra isNotNull guard is needed here.
                cb.like(cb.lower(root.get("personalEmail")), pattern)
        );
    }
}
