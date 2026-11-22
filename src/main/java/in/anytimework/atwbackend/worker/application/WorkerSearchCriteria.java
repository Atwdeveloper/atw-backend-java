package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Each field is an optional filter - null or empty means "don't filter on this". Every multi-valued
 * field matches ANY of the given values for that field (an OR within the field), and every non-empty
 * field is ANDed together - see {@link WorkerService#list} / {@code WorkerService.buildPredicates}.
 * {@code registeredFrom}/{@code registeredTo} filter on {@code createdAt} (the worker's "registration
 * date" - there is no separate stored registration-date column; legacy's own equivalent filter is
 * likewise just a renamed filter over its creation timestamp, confirmed by reading its code directly).
 * {@code stateId}/{@code cityId} match a worker whose PERMANENT **or** CURRENT address (see
 * {@code AddressType}) references any of the given ids - a worker with two addresses on file is not
 * required to match on both, just either one.
 */
public record WorkerSearchCriteria(
        Set<WorkerStatus> status,
        Set<UUID> departmentId,
        Set<UUID> industryId,
        Set<UUID> designationId,
        Set<UUID> skillId,
        Set<KycStatus> kycStatus,
        String search,
        LocalDate registeredFrom,
        LocalDate registeredTo,
        Set<UUID> stateId,
        Set<UUID> cityId
) {
}
