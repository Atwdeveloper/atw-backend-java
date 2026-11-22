package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.Set;
import java.util.UUID;

/** Same shape as {@code DepartmentSearchCriteria}, with the two relationship filters a designation also supports. */
public record DesignationSearchCriteria(Set<UUID> industryIds, Set<UUID> skillCategoryIds, Set<ActiveStatus> statuses, String search) {
}
