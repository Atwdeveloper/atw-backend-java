package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.domain.MinimumWageRate;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

final class MinimumWageRateSpecifications {

    private MinimumWageRateSpecifications() {
    }

    static Specification<MinimumWageRate> hasStateIds(Set<UUID> stateIds) {
        return (root, query, cb) -> root.get("stateId").in(stateIds);
    }

    static Specification<MinimumWageRate> hasSkillCategoryIds(Set<UUID> skillCategoryIds) {
        return (root, query, cb) -> root.get("skillCategoryId").in(skillCategoryIds);
    }

    static Specification<MinimumWageRate> hasStatuses(Set<ActiveStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }
}
