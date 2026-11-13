package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;

import java.util.Set;
import java.util.UUID;

/**
 * Each field is an optional filter - null or empty means "don't filter on this". Every field is
 * multi-valued: a non-empty set matches ANY of the given values for that field (an OR within the
 * field), and every non-empty field is ANDed together - see {@link WorkerService#list} /
 * {@code WorkerService.buildPredicates}.
 */
public record WorkerSearchCriteria(
        Set<WorkerStatus> status,
        Set<UUID> departmentId,
        Set<UUID> industryId,
        Set<UUID> designationId,
        Set<UUID> skillId,
        Set<KycStatus> kycStatus,
        String search
) {
}
