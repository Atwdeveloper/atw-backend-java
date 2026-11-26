package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.SortValidation;
import in.anytimework.atwbackend.shared.persistence.Specifications;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for skill categories - purely a minimum-wage grouping, unrelated to the
 * industry/designation/skill chain. Authorization ({@code skill_category.view/create/edit/toggle_status/delete})
 * is enforced at the controller.
 */
@Service
public class SkillCategoryService {

    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final SkillCategoryRepository skillCategories;
    private final DesignationRepository designations;
    private final TransactionTemplate requiresNewTransaction;

    SkillCategoryService(SkillCategoryRepository skillCategories, DesignationRepository designations,
                        PlatformTransactionManager transactionManager) {
        this.skillCategories = skillCategories;
        this.designations = designations;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillCategoryResponse> list(SkillCategorySearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<SkillCategory>> predicates = new ArrayList<>();
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(SkillCategorySpecifications.hasStatuses(criteria.statuses()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(SkillCategorySpecifications.nameContains(criteria.search()));
        }
        Page<SkillCategory> page = skillCategories.findAll(Specifications.allOf(predicates), pageable);
        // Batched, not toDto(SkillCategory) per row - one query for the whole page's designations,
        // grouped client-side, instead of one query per skill category (see toDto(SkillCategory) for
        // the single-row counterpart used by get/create/update/etc., where that cost is fine).
        Set<UUID> skillCategoryIds = page.getContent().stream().map(SkillCategory::getId).collect(Collectors.toSet());
        Map<UUID, List<DesignationSummary>> designationsById = skillCategoryIds.isEmpty() ? Map.of()
                : designations.findBySkillCategoryIdIn(skillCategoryIds).stream()
                        .collect(Collectors.groupingBy(Designation::getSkillCategoryId,
                                Collectors.mapping(SkillCategoryService::toSummary, Collectors.toList())));
        return PageResponse.from(page.map(sc -> toDto(sc, designationsById.getOrDefault(sc.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public SkillCategoryResponse get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public SkillCategoryResponse create(CreateSkillCategoryRequest request) {
        if (skillCategories.existsByName(request.name())) {
            throw new ConflictException("A skill category named '" + request.name() + "' already exists.");
        }
        SkillCategory skillCategory = new SkillCategory(request.name(), generateSkillCategoryCode());
        return toDto(skillCategories.save(skillCategory));
    }

    @Transactional
    public SkillCategoryResponse update(UUID id, UpdateSkillCategoryRequest request) {
        SkillCategory skillCategory = findOrThrow(id);
        if (skillCategories.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A skill category named '" + request.name() + "' already exists.");
        }
        skillCategory.rename(request.name());
        return toDto(skillCategories.save(skillCategory));
    }

    /** Server-generated, never user-supplied - same mechanism as {@code Worker.workerCode}: a dedicated Postgres sequence, never reused, never a join key. */
    private String generateSkillCategoryCode() {
        return "SKC" + String.format("%07d", skillCategories.nextSkillCategoryCodeSequenceValue());
    }

    @Transactional
    public SkillCategoryResponse updateStatus(UUID id, ActiveStatus status) {
        SkillCategory skillCategory = findOrThrow(id);
        applyStatus(skillCategory, status);
        return toDto(skillCategories.save(skillCategory));
    }

    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, ActiveStatus status) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                SkillCategory skillCategory = findOrThrow(id);
                applyStatus(skillCategory, status);
                skillCategories.save(skillCategory);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    @Transactional
    public void delete(UUID id) {
        SkillCategory skillCategory = findOrThrow(id);
        try {
            skillCategories.delete(skillCategory);
            skillCategories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this skill category because it may still be used by designations or minimum wage rates. "
                            + "Deactivate it instead.");
        }
    }

    public BulkActionResult bulkDelete(Set<UUID> ids) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                requiresNewTransaction.executeWithoutResult(status -> delete(id));
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            } catch (ConflictException e) {
                failures.add(new BulkActionFailure(id, e.getMessage()));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    private void applyStatus(SkillCategory skillCategory, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            skillCategory.activate();
        } else {
            skillCategory.deactivate();
        }
    }

    private SkillCategory findOrThrow(UUID id) {
        return skillCategories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill category not found"));
    }

    /** Single-row resolution (get/create/update/...) - only {@link #list} needs the batched designationsById map. */
    private SkillCategoryResponse toDto(SkillCategory skillCategory) {
        List<DesignationSummary> designationSummaries = designations.findBySkillCategoryId(skillCategory.getId())
                .stream().map(SkillCategoryService::toSummary).toList();
        return toDto(skillCategory, designationSummaries);
    }

    private SkillCategoryResponse toDto(SkillCategory skillCategory, List<DesignationSummary> designationSummaries) {
        return new SkillCategoryResponse(skillCategory.getId(), skillCategory.getName(), skillCategory.getCode(),
                skillCategory.getStatus(), designationSummaries, designationSummaries.size());
    }

    private static DesignationSummary toSummary(Designation designation) {
        return new DesignationSummary(designation.getId(), designation.getName());
    }
}
