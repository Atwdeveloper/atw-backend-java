package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminStatus;

import java.util.Set;
import java.util.UUID;

/**
 * Each field is an optional filter - null or empty means "don't filter on this". Multi-valued fields
 * match ANY of the given values (an OR within the field), and every non-empty field is ANDed together
 * - see {@link AdminStaffService#list}/{@code AdminStaffService.buildPredicates}. Mirrors
 * {@code worker.application.WorkerSearchCriteria}'s conventions, the first precedent for this kind of
 * dynamic filtering in the codebase.
 */
public record AdminStaffSearchCriteria(
        Set<AdminStatus> statuses,
        Set<UUID> roleIds,
        Boolean superAdmin,
        String search
) {
}
