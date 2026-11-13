package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerDesignation;
import in.anytimework.atwbackend.worker.domain.WorkerSkill;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

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

    static Specification<Worker> hasDepartment(Set<UUID> departmentIds) {
        return (root, query, cb) -> root.get("departmentId").in(departmentIds);
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
