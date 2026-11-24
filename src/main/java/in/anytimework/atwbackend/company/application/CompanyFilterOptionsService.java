package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.api.admin.dto.CompanyFilterOptionsResponse;
import in.anytimework.atwbackend.company.domain.Company;
import in.anytimework.atwbackend.company.domain.CompanyAddress;
import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.shared.persistence.DistinctColumnValues;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scoped filter-dropdown options for the company list - "only the industries/states/cities actually
 * referenced by at least one non-deleted company today", never the full masterdata reference list a
 * bare {@code *LookupService.list()} would return. Mirrors
 * {@code worker.application.WorkerFilterOptionsService}'s exact pattern. {@code industryId} is queried
 * directly against {@link Company} (already filtered by its own {@code @SQLRestriction}); state/city
 * live on {@link CompanyAddress}, a satellite table with no such restriction of its own, so that query
 * needs an extra predicate confirming the referencing company still exists and is visible.
 */
@Service
public class CompanyFilterOptionsService {

    private final EntityManager entityManager;
    private final IndustryLookupService industryLookup;
    private final GeographyLookupService geography;

    public CompanyFilterOptionsService(EntityManager entityManager, IndustryLookupService industryLookup,
                                       GeographyLookupService geography) {
        this.entityManager = entityManager;
        this.industryLookup = industryLookup;
        this.geography = geography;
    }

    @Transactional(readOnly = true)
    public CompanyFilterOptionsResponse get() {
        Set<UUID> industryIds = Set.copyOf(DistinctColumnValues.of(entityManager, Company.class, "industryId", UUID.class));
        Set<UUID> stateIds = Set.copyOf(DistinctColumnValues.of(entityManager, CompanyAddress.class,
                "address.stateId", UUID.class, List.of(parentCompanyVisible())));
        Set<UUID> cityIds = Set.copyOf(DistinctColumnValues.of(entityManager, CompanyAddress.class,
                "address.cityId", UUID.class, List.of(parentCompanyVisible())));

        List<IndustrySummary> industries = industryLookup.list().stream()
                .filter(i -> industryIds.contains(i.id())).toList();
        List<StateSummary> states = geography.listStates().stream()
                .filter(s -> stateIds.contains(s.id())).toList();
        List<CitySummary> cities = geography.listCitiesByIds(cityIds);

        return new CompanyFilterOptionsResponse(industries, states, cities);
    }

    /**
     * Restricts the {@link CompanyAddress} query (which carries no {@code @SQLRestriction} of its own)
     * to rows whose {@code companyId} still references a {@link Company} that exists and isn't
     * soft-deleted - without this, a state/city referenced ONLY by a soft-deleted company's old
     * address row would still surface as a "currently in use" filter option.
     */
    private static <E> Specification<E> parentCompanyVisible() {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Company> company = subquery.from(Company.class);
            subquery.select(company.get("id"));
            return root.get("companyId").in(subquery);
        };
    }
}
