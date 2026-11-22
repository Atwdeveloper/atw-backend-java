package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.Set;
import java.util.UUID;

/**
 * Multiselect listing filters for {@code AdminMinimumWageRateController.list()}. Unlike the other
 * masterdata entities, there is no free-text {@code search} field here - a rate row has no
 * name/label of its own to search by, only the (state, skill category) pair and status, which are
 * already covered by the other three fields. {@code statuses} is included because {@code status} is
 * a real, meaningful filter for this entity: an admin reconstructing history may want only the
 * currently-active version of each pair, or conversely only the retracted ({@code INACTIVE}) ones.
 */
public record MinimumWageRateSearchCriteria(Set<UUID> stateIds, Set<UUID> skillCategoryIds, Set<ActiveStatus> statuses) {
}
