package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Each field is an optional filter - null or empty means "don't filter on this". Every multi-valued
 * field matches ANY of the given values for that field (an OR within the field), and every non-empty
 * field is ANDed together - mirrors {@code worker.application.WorkerSearchCriteria}'s exact contract.
 * {@code search} matches against {@code name} or {@code companyCode} (case-insensitive, substring).
 * {@code gstVerified}: {@code true} matches a company with {@code gstVerifiedAt} set, {@code false}
 * matches one with it still null, absent (null) means don't filter on verification state at all.
 * {@code createdFrom}/{@code createdTo} filter on {@code Company.createdAt} as whole UTC calendar days -
 * same semantics as {@code WorkerSearchCriteria.registeredFrom}/{@code registeredTo} (see
 * {@code CompanySpecifications.createdBetween}). {@code stateId}/{@code cityId} match a company with
 * ANY {@link in.anytimework.atwbackend.company.domain.CompanyAddress} referencing any of the given ids -
 * unlike {@code WorkerSearchCriteria}'s permanent-vs-current address split, a company's addresses carry
 * no such type distinction to restrict to (see {@code CompanyAddress}'s Javadoc).
 *
 * <p>Unlike {@code WorkerSearchCriteria}, every filter here resolves to a predicate against
 * {@link in.anytimework.atwbackend.company.domain.Company}/{@link in.anytimework.atwbackend.company.domain.CompanyAddress}
 * alone (a direct column, or a same-module satellite-table subquery) - there is no cross-module
 * id-list filter (e.g. worker's {@code kycStatus}) that could resolve to zero ids, so
 * {@code CompanyService.list} needs no {@code Optional}-wrapped short-circuit the way
 * {@code WorkerService.buildPredicates} does.</p>
 */
public record CompanySearchCriteria(
        Set<CompanyStatus> status,
        Set<UUID> industryId,
        String search,
        Boolean gstVerified,
        LocalDate createdFrom,
        LocalDate createdTo,
        Set<UUID> stateId,
        Set<UUID> cityId
) {
}
