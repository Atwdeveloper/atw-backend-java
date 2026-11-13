package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic multiselect filtering for the admin designation list, same shape as
 * {@code DepartmentSpecifications}.
 *
 * <p>{@code hasIndustries} joins through {@link Designation#getIndustries()} - a genuinely navigable
 * {@code @ManyToMany}, unlike {@code WorkerDesignation}/{@code WorkerSkill} (which have no mapped
 * entity of their own to subquery against, see {@code WorkerSpecifications}) - so the join is the
 * natural approach here. The query is marked {@code distinct}: without it, a designation belonging to
 * more than one of the selected industry ids would come back once per matching industry, inflating
 * both the page content and {@code totalElements}.</p>
 */
final class DesignationSpecifications {

    private DesignationSpecifications() {
    }

    static Specification<Designation> hasIndustries(Set<UUID> industryIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            return root.join("industries").get("id").in(industryIds);
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
