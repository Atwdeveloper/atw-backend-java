package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationIndustry;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic multiselect filtering for the admin designation list, same shape as
 * {@code DepartmentSpecifications}.
 *
 * <p>{@code hasIndustries} is an uncorrelated subquery against {@link DesignationIndustry} - the same
 * technique {@code WorkerSpecifications.hasDesignation}/{@code hasSkill} already use for a
 * no-mapped-entity-of-its-own-to-join-against relationship. A subquery also means no fan-out (a
 * designation matching 2+ selected industry ids still returns exactly one row), so unlike the old
 * {@code @ManyToMany}-join version this predicate needs no {@code query.distinct(true)}.</p>
 */
final class DesignationSpecifications {

    private DesignationSpecifications() {
    }

    static Specification<Designation> hasIndustries(Set<UUID> industryIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var designationIndustry = subquery.from(DesignationIndustry.class);
            subquery.select(designationIndustry.get("designationId"))
                    .where(designationIndustry.get("industryId").in(industryIds));
            return root.get("id").in(subquery);
        };
    }

    static Specification<Designation> hasSkillCategories(Set<UUID> skillCategoryIds) {
        return (root, query, cb) -> root.get("skillCategoryId").in(skillCategoryIds);
    }

    static Specification<Designation> hasStatuses(Set<ActiveStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Designation> nameContains(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }
}
