package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateResponse;
import in.anytimework.atwbackend.masterdata.domain.MinimumWageRate;
import in.anytimework.atwbackend.masterdata.domain.MinimumWageRateRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.SortValidation;
import in.anytimework.atwbackend.shared.persistence.Specifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Statutory minimum daily wage per (state, skill category), effective-dated. Deliberately has no
 * update/edit of an existing rate's value - a rate change is always a new version (see
 * {@link MinimumWageRate}'s Javadoc for why), and consequently no delete/bulk-delete either: this
 * table is an append-only statutory history, and deleting a row would defeat the exact
 * "reconstruct what rate legally applied on any past date" purpose it exists for.
 * {@code minimum_wage.delete} exists in the permission catalog (inherited from the legacy module
 * list) but is deliberately left unwired, same as {@code industry.delete} etc. elsewhere in this
 * module. {@code minimum_wage.edit} is likewise unused - there is no edit-the-value endpoint at all.
 * Authorization ({@code minimum_wage.view/create/toggle_status}) is enforced at the controller.
 */
@Service
public class MinimumWageRateService {

    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why (notably excludes the raw stateId/skillCategoryId UUIDs - already filterable via multiselect). */
    private static final Set<String> ALLOWED_SORT = Set.of("effectiveFrom", "minDailyRate", "status", "createdAt");

    private final MinimumWageRateRepository rates;
    private final StateRepository states;
    private final SkillCategoryRepository skillCategories;
    private final Clock clock;

    MinimumWageRateService(MinimumWageRateRepository rates, StateRepository states,
                            SkillCategoryRepository skillCategories, Clock clock) {
        this.rates = rates;
        this.states = states;
        this.skillCategories = skillCategories;
        this.clock = clock;
    }

    /**
     * Paged, unlike every other list() in this module: minimum_wage_rate is the one reference table
     * here that isn't bounded by how many distinct real-world entities exist - it's append-only,
     * growing with every rate change over the years (immutable rows, a correction is always a new
     * version - see {@link MinimumWageRate}'s Javadoc), so an unbounded "return everything" response
     * would only get slower over time.
     */
    @Transactional(readOnly = true)
    public PageResponse<MinimumWageRateResponse> list(MinimumWageRateSearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<MinimumWageRate>> predicates = new ArrayList<>();
        if (criteria.stateIds() != null && !criteria.stateIds().isEmpty()) {
            predicates.add(MinimumWageRateSpecifications.hasStateIds(criteria.stateIds()));
        }
        if (criteria.skillCategoryIds() != null && !criteria.skillCategoryIds().isEmpty()) {
            predicates.add(MinimumWageRateSpecifications.hasSkillCategoryIds(criteria.skillCategoryIds()));
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(MinimumWageRateSpecifications.hasStatuses(criteria.statuses()));
        }
        Page<MinimumWageRate> page = rates.findAll(Specifications.allOf(predicates), pageable);
        // Batched once for the whole page, not per row - see nameOf()/toDtos() history in this class.
        Map<UUID, String> stateNames = nameOf(states, page.getContent().stream().map(MinimumWageRate::getStateId).distinct().toList());
        Map<UUID, String> categoryNames = nameOf(skillCategories,
                page.getContent().stream().map(MinimumWageRate::getSkillCategoryId).distinct().toList());
        return PageResponse.from(page.map(r -> toDto(r, stateNames, categoryNames)));
    }

    @Transactional(readOnly = true)
    public MinimumWageRateResponse get(UUID id) {
        MinimumWageRate rate = findOrThrow(id);
        return toDto(rate, nameOf(states, List.of(rate.getStateId())), nameOf(skillCategories, List.of(rate.getSkillCategoryId())));
    }

    /** Resolves the applicable version as of {@code asOf} (defaults to today if null). */
    @Transactional(readOnly = true)
    public MinimumWageRateResponse resolveEffective(UUID stateId, UUID skillCategoryId, LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now(clock);
        MinimumWageRate rate = rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        stateId, skillCategoryId, ActiveStatus.ACTIVE, effectiveAsOf)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No minimum wage rate configured for this state/skill-category as of " + effectiveAsOf));
        return toDto(rate, nameOf(states, List.of(stateId)), nameOf(skillCategories, List.of(skillCategoryId)));
    }

    @Transactional
    public MinimumWageRateResponse create(CreateMinimumWageRateRequest request) {
        if (!states.existsById(request.stateId())) {
            throw new ValidationException("Unknown state id: " + request.stateId());
        }
        if (!skillCategories.existsById(request.skillCategoryId())) {
            throw new ValidationException("Unknown skill category id: " + request.skillCategoryId());
        }
        if (rates.existsByStateIdAndSkillCategoryIdAndEffectiveFromAndStatus(
                request.stateId(), request.skillCategoryId(), request.effectiveFrom(), ActiveStatus.ACTIVE)) {
            throw new ConflictException("An active rate for this state/skill-category already exists effective from "
                    + request.effectiveFrom() + ". Deactivate it first if this is a correction, then create the new version.");
        }
        MinimumWageRate rate = new MinimumWageRate(request.stateId(), request.skillCategoryId(),
                request.minDailyRate(), request.effectiveFrom());
        rate = rates.save(rate);
        return toDto(rate, nameOf(states, List.of(rate.getStateId())), nameOf(skillCategories, List.of(rate.getSkillCategoryId())));
    }

    @Transactional
    public MinimumWageRateResponse updateStatus(UUID id, ActiveStatus status) {
        MinimumWageRate rate = findOrThrow(id);
        applyStatus(rate, status);
        rate = rates.save(rate);
        return toDto(rate, nameOf(states, List.of(rate.getStateId())), nameOf(skillCategories, List.of(rate.getSkillCategoryId())));
    }

    /**
     * Bulk version of the single status toggle above - e.g. retracting several wrongly-entered rates
     * at once. No bulk-delete counterpart: this entity never has a delete endpoint at all (see this
     * class's Javadoc), so there is deliberately nothing to add here beyond status.
     */
    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, ActiveStatus status) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                MinimumWageRate rate = findOrThrow(id);
                applyStatus(rate, status);
                rates.save(rate);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    private void applyStatus(MinimumWageRate rate, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            rate.activate();
        } else {
            rate.deactivate();
        }
    }

    private MinimumWageRate findOrThrow(UUID id) {
        return rates.findById(id).orElseThrow(() -> new ResourceNotFoundException("Minimum wage rate not found"));
    }

    private Map<UUID, String> nameOf(StateRepository repository, List<UUID> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(State::getId, State::getName));
    }

    private Map<UUID, String> nameOf(SkillCategoryRepository repository, List<UUID> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(SkillCategory::getId, SkillCategory::getName));
    }

    private MinimumWageRateResponse toDto(MinimumWageRate rate, Map<UUID, String> stateNames, Map<UUID, String> categoryNames) {
        return new MinimumWageRateResponse(rate.getId(), rate.getStateId(), stateNames.get(rate.getStateId()),
                rate.getSkillCategoryId(), categoryNames.get(rate.getSkillCategoryId()),
                rate.getMinDailyRate(), rate.getEffectiveFrom(), rate.getStatus());
    }
}
