package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.shared.persistence.DistinctColumnValues;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerFilterOptionsResponse;
import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerAddress;
import in.anytimework.atwbackend.worker.domain.WorkerDesignation;
import in.anytimework.atwbackend.worker.domain.WorkerSkill;
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
 * Scoped filter-dropdown options for the worker list - "only the industries/designations/skills/
 * states/cities actually referenced by at least one non-deleted worker today", never the full
 * masterdata reference list a bare {@code *LookupService.list()} would return. {@code industryId} is
 * queried directly against {@link Worker} (already filtered by its own {@code @SQLRestriction}); the
 * other four live on satellite tables ({@link WorkerDesignation}/{@link WorkerSkill}/
 * {@link WorkerAddress}) that carry no such restriction of their own, so each of those needs an extra
 * predicate confirming the referencing worker still exists and is visible.
 */
@Service
public class WorkerFilterOptionsService {

    private final EntityManager entityManager;
    private final IndustryLookupService industryLookup;
    private final DesignationLookupService designationLookup;
    private final SkillLookupService skillLookup;
    private final GeographyLookupService geography;

    public WorkerFilterOptionsService(EntityManager entityManager, IndustryLookupService industryLookup,
                                      DesignationLookupService designationLookup, SkillLookupService skillLookup,
                                      GeographyLookupService geography) {
        this.entityManager = entityManager;
        this.industryLookup = industryLookup;
        this.designationLookup = designationLookup;
        this.skillLookup = skillLookup;
        this.geography = geography;
    }

    @Transactional(readOnly = true)
    public WorkerFilterOptionsResponse get() {
        Set<UUID> industryIds = Set.copyOf(DistinctColumnValues.of(entityManager, Worker.class, "industryId", UUID.class));
        Set<UUID> designationIds = Set.copyOf(DistinctColumnValues.of(entityManager, WorkerDesignation.class,
                "designationId", UUID.class, List.of(parentWorkerVisible())));
        Set<UUID> skillIds = Set.copyOf(DistinctColumnValues.of(entityManager, WorkerSkill.class,
                "skillId", UUID.class, List.of(parentWorkerVisible())));
        Set<UUID> stateIds = Set.copyOf(DistinctColumnValues.of(entityManager, WorkerAddress.class,
                "address.stateId", UUID.class, List.of(parentWorkerVisible())));
        Set<UUID> cityIds = Set.copyOf(DistinctColumnValues.of(entityManager, WorkerAddress.class,
                "address.cityId", UUID.class, List.of(parentWorkerVisible())));

        List<IndustrySummary> industries = industryLookup.list().stream()
                .filter(i -> industryIds.contains(i.id())).toList();
        List<DesignationSummary> designations = designationLookup.list().stream()
                .filter(d -> designationIds.contains(d.id())).toList();
        List<SkillSummary> skills = skillLookup.list().stream()
                .filter(s -> skillIds.contains(s.id())).toList();
        List<StateSummary> states = geography.listStates().stream()
                .filter(s -> stateIds.contains(s.id())).toList();
        List<CitySummary> cities = geography.listCitiesByIds(cityIds);

        return new WorkerFilterOptionsResponse(industries, designations, skills, states, cities);
    }

    /**
     * Restricts a satellite-table query (which carries no {@code @SQLRestriction} of its own) to rows
     * whose {@code workerId} still references a {@link Worker} that exists and isn't soft-deleted -
     * without this, a designation/skill/state/city referenced ONLY by a soft-deleted worker's old
     * satellite row would still surface as a "currently in use" filter option.
     */
    private static <E> Specification<E> parentWorkerVisible() {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Worker> worker = subquery.from(Worker.class);
            subquery.select(worker.get("id"));
            return root.get("workerId").in(subquery);
        };
    }
}
