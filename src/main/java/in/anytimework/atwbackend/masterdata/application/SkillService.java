package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.Skill;
import in.anytimework.atwbackend.masterdata.domain.SkillRepository;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
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
 * CRUD for skills, each belonging to exactly one designation. Authorization
 * ({@code skill.view/create/edit/delete/toggle_status}) is enforced at the controller.
 */
@Service
public class SkillService {

    private static final String IMAGE_CATEGORY = "SKILL_ICON";
    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why (notably excludes the batch-resolved designationName). */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final SkillRepository skills;
    private final DesignationRepository designations;
    private final DesignationLookupService designationLookup;
    private final MasterDataImageSupport images;
    private final TransactionTemplate requiresNewTransaction;

    SkillService(SkillRepository skills, DesignationRepository designations, DesignationLookupService designationLookup,
            MasterDataImageSupport images, PlatformTransactionManager transactionManager) {
        this.skills = skills;
        this.designations = designations;
        this.designationLookup = designationLookup;
        this.images = images;
        // REQUIRES_NEW: each bulkDelete() attempt must commit/fail independently of the others - see
        // that method's Javadoc.
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillResponse> list(SkillSearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<Skill>> predicates = new ArrayList<>();
        if (criteria.designationIds() != null && !criteria.designationIds().isEmpty()) {
            predicates.add(SkillSpecifications.hasDesignationIds(criteria.designationIds()));
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(SkillSpecifications.hasStatuses(criteria.statuses()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(SkillSpecifications.nameContains(criteria.search()));
        }
        Page<Skill> page = skills.findAll(Specifications.allOf(predicates), pageable);
        // Batch-resolve every designation name for the whole page in ONE lookup call, precomputed
        // once outside the per-row mapping below - same shape as WorkerService.toSummaryResponses().
        Map<UUID, String> designationNameById = designationLookup.list().stream()
                .collect(Collectors.toMap(DesignationSummary::id, DesignationSummary::name));
        return PageResponse.from(page.map(skill -> toDto(skill, designationNameById.get(skill.getDesignationId()))));
    }

    @Transactional(readOnly = true)
    public SkillResponse get(UUID id) {
        Skill skill = findOrThrow(id);
        return toDto(skill, designationNameOf(skill));
    }

    @Transactional
    public SkillResponse create(CreateSkillRequest request) {
        requireDesignationExists(request.designationId());
        if (skills.existsByDesignationIdAndName(request.designationId(), request.name())) {
            throw new ConflictException("A skill named '" + request.name() + "' already exists under this designation.");
        }
        Skill skill = new Skill(request.designationId(), request.name());
        skill = skills.save(skill);
        return toDto(skill, designationNameOf(skill));
    }

    @Transactional
    public SkillResponse update(UUID id, UpdateSkillRequest request) {
        Skill skill = findOrThrow(id);
        requireDesignationExists(request.designationId());
        if (skills.existsByDesignationIdAndNameAndIdNot(request.designationId(), request.name(), id)) {
            throw new ConflictException("A skill named '" + request.name() + "' already exists under this designation.");
        }
        skill.update(request.designationId(), request.name());
        skill = skills.save(skill);
        return toDto(skill, designationNameOf(skill));
    }

    @Transactional
    public SkillResponse updateStatus(UUID id, ActiveStatus status) {
        Skill skill = findOrThrow(id);
        applyStatus(skill, status);
        skill = skills.save(skill);
        return toDto(skill, designationNameOf(skill));
    }

    /**
     * Status changes never hit a constraint (no unique/FK risk), so - unlike {@link #bulkDelete} -
     * every item here safely shares one ordinary transaction; a {@link ResourceNotFoundException} is a
     * clean, pre-write Java exception, not a failed flush, so it can't poison the shared persistence
     * context for the remaining ids.
     */
    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, ActiveStatus status) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                Skill skill = findOrThrow(id);
                applyStatus(skill, status);
                skills.save(skill);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    @Transactional
    public SkillResponse uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Skill skill = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, skill.getImageDocumentId(), filename, contentType, content);
        skill.setImageDocumentId(newImageId);
        skill = skills.save(skill);
        return toDto(skill, designationNameOf(skill));
    }

    @Transactional
    public SkillResponse removeImage(UUID id) {
        Skill skill = findOrThrow(id);
        images.remove(skill.getImageDocumentId());
        skill.setImageDocumentId(null);
        skill = skills.save(skill);
        return toDto(skill, designationNameOf(skill));
    }

    /**
     * Relies entirely on the existing {@code worker_skill.skill_id REFERENCES skill(id) ON DELETE
     * RESTRICT} constraint, not a cross-module existence pre-check: masterdata must never query
     * another module's table directly (one owner per table), and a separate pre-check would race
     * against a concurrent insert anyway. {@code flush()} forces Postgres to evaluate the constraint
     * inside this method's own try/catch, rather than letting it surface later, outside this method, at
     * the next unrelated flush point.
     *
     * <p>{@code @Transactional} here takes effect when this is called directly (the single-delete
     * controller endpoint); when called instead from {@link #bulkDelete}, it's bypassed (plain
     * self-invocation never goes through the proxy) and {@code requiresNewTransaction} provides the
     * real transaction boundary instead - both paths are intentional, see {@link #bulkDelete}'s
     * Javadoc.</p>
     */
    @Transactional
    public void delete(UUID id) {
        Skill skill = findOrThrow(id);
        try {
            skills.delete(skill);
            skills.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this skill because it may still be assigned to workers. "
                            + "Remove it from those workers first, or deactivate it instead.");
        }
    }

    /**
     * Deliberately NOT {@code @Transactional} itself - each id's delete runs in its OWN transaction via
     * {@link #requiresNewTransaction}, not one shared across the whole batch. Once a flush fails with
     * {@code DataIntegrityViolationException}, Hibernate marks that persistence context rollback-only
     * for the rest of ITS transaction, even though the exception is caught right here - so if this
     * method's own body ran inside one ambient transaction, the NEXT id in the loop would fail too,
     * regardless of whether it could have succeeded entirely on its own.
     */
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

    private void applyStatus(Skill skill, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            skill.activate();
        } else {
            skill.deactivate();
        }
    }

    private void requireDesignationExists(UUID designationId) {
        if (!designations.existsById(designationId)) {
            throw new ValidationException("Unknown designation id: " + designationId);
        }
    }

    private Skill findOrThrow(UUID id) {
        return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    /** Single-row resolution (get/create/update/...) - only {@link #list} needs the batched form. */
    private String designationNameOf(Skill skill) {
        return designationLookup.get(skill.getDesignationId()).name();
    }

    private SkillResponse toDto(Skill skill, String designationName) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getDesignationId(), designationName,
                skill.getStatus(), images.urlOrNull(skill.getImageDocumentId()));
    }
}
