package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.Set;
import java.util.UUID;

public record SkillSearchCriteria(Set<UUID> designationIds, Set<ActiveStatus> statuses, String search) {
}
