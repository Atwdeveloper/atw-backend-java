package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateDto;
import in.anytimework.atwbackend.masterdata.domain.MinimumWageRate;
import in.anytimework.atwbackend.masterdata.domain.MinimumWageRateRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Statutory minimum daily wage per (state, skill category), effective-dated. Deliberately has no
 * update/edit of an existing rate's value - a rate change is always a new version (see
 * {@link MinimumWageRate}'s Javadoc for why). Authorization
 * ({@code minimum_wage.view/create/toggle_status}) is enforced at the controller;
 * {@code minimum_wage.edit}/{@code .delete} exist in the permission catalog (inherited from the
 * legacy module list) but are deliberately unused here, same as {@code industry.delete} etc.
 * elsewhere in this module.
 */
@Service
public class MinimumWageRateService {

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
    public PageResponse<MinimumWageRateDto> list(UUID stateIdFilter, UUID skillCategoryIdFilter, Pageable pageable) {
        Page<MinimumWageRate> page;
        if (stateIdFilter != null && skillCategoryIdFilter != null) {
            page = rates.findByStateIdAndSkillCategoryId(stateIdFilter, skillCategoryIdFilter, pageable);
        } else if (stateIdFilter != null) {
            page = rates.findByStateId(stateIdFilter, pageable);
        } else if (skillCategoryIdFilter != null) {
            page = rates.findBySkillCategoryId(skillCategoryIdFilter, pageable);
        } else {
            page = rates.findAll(pageable);
        }
        // Batched once for the whole page, not per row - see nameOf()/toDtos() history in this class.
        Map<UUID, String> stateNames = nameOf(states, page.getContent().stream().map(MinimumWageRate::getStateId).distinct().toList());
        Map<UUID, String> categoryNames = nameOf(skillCategories,
                page.getContent().stream().map(MinimumWageRate::getSkillCategoryId).distinct().toList());
        return PageResponse.from(page.map(r -> toDto(r, stateNames, categoryNames)));
    }

    @Transactional(readOnly = true)
    public MinimumWageRateDto get(UUID id) {
        MinimumWageRate rate = findOrThrow(id);
        return toDto(rate, nameOf(states, List.of(rate.getStateId())), nameOf(skillCategories, List.of(rate.getSkillCategoryId())));
    }

    /** Resolves the applicable version as of {@code asOf} (defaults to today if null). */
    @Transactional(readOnly = true)
    public MinimumWageRateDto resolveEffective(UUID stateId, UUID skillCategoryId, LocalDate asOf) {
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now(clock);
        MinimumWageRate rate = rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        stateId, skillCategoryId, ActiveStatus.ACTIVE, effectiveAsOf)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No minimum wage rate configured for this state/skill-category as of " + effectiveAsOf));
        return toDto(rate, nameOf(states, List.of(stateId)), nameOf(skillCategories, List.of(skillCategoryId)));
    }

    @Transactional
    public MinimumWageRateDto create(CreateMinimumWageRateRequest request) {
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
    public MinimumWageRateDto updateStatus(UUID id, ActiveStatus status) {
        MinimumWageRate rate = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            rate.activate();
        } else {
            rate.deactivate();
        }
        rate = rates.save(rate);
        return toDto(rate, nameOf(states, List.of(rate.getStateId())), nameOf(skillCategories, List.of(rate.getSkillCategoryId())));
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

    private MinimumWageRateDto toDto(MinimumWageRate rate, Map<UUID, String> stateNames, Map<UUID, String> categoryNames) {
        return new MinimumWageRateDto(rate.getId(), rate.getStateId(), stateNames.get(rate.getStateId()),
                rate.getSkillCategoryId(), categoryNames.get(rate.getSkillCategoryId()),
                rate.getMinDailyRate(), rate.getEffectiveFrom(), rate.getStatus());
    }
}
