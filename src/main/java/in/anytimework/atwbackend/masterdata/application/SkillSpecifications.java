package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.domain.Skill;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class SkillSpecifications {

    private SkillSpecifications() {
    }

    static Specification<Skill> hasDesignationIds(Set<UUID> designationIds) {
        return (root, query, cb) -> root.get("designationId").in(designationIds);
    }

    static Specification<Skill> hasStatuses(Set<ActiveStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Skill> nameContains(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }
}
