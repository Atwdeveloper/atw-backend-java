package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.Set;

public record DepartmentSearchCriteria(Set<ActiveStatus> statuses, String search) {
}
