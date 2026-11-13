package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD for designations, including the designation-to-industry assignment. Authorization
 * ({@code designation.view/create/edit/toggle_status/delete}) is enforced at the controller.
 */
@Service
public class DesignationService {

    private static final String IMAGE_CATEGORY = "DESIGNATION_ICON";
    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final DesignationRepository designations;
    private final IndustryRepository industries;
    private final SkillCategoryRepository skillCategories;
    private final MasterDataImageSupport images;
    private final TransactionTemplate requiresNewTransaction;

    DesignationService(DesignationRepository designations, IndustryRepository industries,
                        SkillCategoryRepository skillCategories, MasterDataImageSupport images,
                        PlatformTransactionManager transactionManager) {
        this.designations = designations;
        this.industries = industries;
        this.skillCategories = skillCategories;
        this.images = images;
        // REQUIRES_NEW: each bulkDelete() attempt must commit/fail independently of the others - see
        // that method's Javadoc (same pattern as DepartmentService.bulkDelete).
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<DesignationResponse> list(DesignationSearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<Designation>> predicates = new ArrayList<>();
        if (criteria.industryIds() != null && !criteria.industryIds().isEmpty()) {
            predicates.add(DesignationSpecifications.hasIndustries(criteria.industryIds()));
        }
        if (criteria.skillCategoryIds() != null && !criteria.skillCategoryIds().isEmpty()) {
            predicates.add(DesignationSpecifications.hasSkillCategories(criteria.skillCategoryIds()));
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(DesignationSpecifications.hasStatuses(criteria.statuses()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(DesignationSpecifications.nameContains(criteria.search()));
        }
        Page<Designation> page = designations.findAll(Specifications.allOf(predicates), pageable);
        // toDto, not a separate list-only projection: image URL resolution is cheap now (the Azure
        // delegation key is cached), so every row gets a resolved imageUrl here too - see get().
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public DesignationResponse get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public DesignationResponse create(CreateDesignationRequest request) {
        if (designations.existsByName(request.name())) {
            throw new ConflictException("A designation named '" + request.name() + "' already exists.");
        }
        Set<Industry> resolved = resolveIndustries(request.industryIds());
        requireSkillCategoryExistsIfPresent(request.skillCategoryId());
        Designation designation = new Designation(request.name());
        designation.replaceIndustries(resolved);
        designation.assignSkillCategory(request.skillCategoryId());
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationResponse update(UUID id, UpdateDesignationRequest request) {
        Designation designation = findOrThrow(id);
        if (designations.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A designation named '" + request.name() + "' already exists.");
        }
        Set<Industry> resolved = resolveIndustries(request.industryIds());
        designation.rename(request.name());
        designation.replaceIndustries(resolved);
        return toDto(designations.save(designation));
    }

    /**
     * Separate from {@link #update}, same reasoning as {@link #uploadImage}/{@link #removeImage}
     * being their own endpoints: skillCategoryId is optional, so folding it into the general update
     * would make "the client didn't mention it" indistinguishable from "clear it", silently wiping a
     * previously-assigned category whenever a caller sends an update without that field.
     */
    @Transactional
    public DesignationResponse assignSkillCategory(UUID id, UUID skillCategoryId) {
        Designation designation = findOrThrow(id);
        requireSkillCategoryExistsIfPresent(skillCategoryId);
        designation.assignSkillCategory(skillCategoryId);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationResponse clearSkillCategory(UUID id) {
        Designation designation = findOrThrow(id);
        designation.assignSkillCategory(null);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationResponse updateStatus(UUID id, ActiveStatus status) {
        Designation designation = findOrThrow(id);
        applyStatus(designation, status);
        return toDto(designations.save(designation));
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
                Designation designation = findOrThrow(id);
                applyStatus(designation, status);
                designations.save(designation);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    @Transactional
    public DesignationResponse uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Designation designation = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, designation.getImageDocumentId(), filename, contentType, content);
        designation.setImageDocumentId(newImageId);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationResponse removeImage(UUID id) {
        Designation designation = findOrThrow(id);
        images.remove(designation.getImageDocumentId());
        designation.setImageDocumentId(null);
        return toDto(designations.save(designation));
    }

    /**
     * Relies entirely on the existing {@code worker_designation.designation_id} / {@code skill.designation_id}
     * {@code REFERENCES designation(id) ON DELETE RESTRICT} constraints, not a cross-module existence
     * pre-check - masterdata must never query another module's table directly (one owner per table),
     * and a separate pre-check would race against a concurrent insert anyway (same reasoning as
     * {@code DepartmentService.delete}). {@code designation_industry} cascades on its own - that's this
     * designation's own join-table rows cleaning up with it, not something to guard against.
     *
     * <p>{@code @Transactional} here takes effect when this is called directly (the single-delete
     * controller endpoint); when called instead from {@link #bulkDelete}, it's bypassed (plain
     * self-invocation never goes through the proxy) and {@code requiresNewTransaction} provides the
     * real transaction boundary instead - see {@link #bulkDelete}'s Javadoc.</p>
     */
    @Transactional
    public void delete(UUID id) {
        Designation designation = findOrThrow(id);
        try {
            designations.delete(designation);
            designations.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this designation because it may still have skills or workers assigned to it. "
                            + "Reassign or remove those first, or deactivate it instead.");
        }
    }

    /**
     * Deliberately NOT {@code @Transactional} itself - each id's delete runs in its OWN transaction via
     * {@link #requiresNewTransaction}, not one shared across the whole batch. Once a flush fails with
     * {@code DataIntegrityViolationException}, Hibernate marks that persistence context rollback-only
     * for the rest of ITS transaction, even though the exception is caught right here - so if this
     * method's own body ran inside one ambient transaction, the NEXT id in the loop would fail too,
     * regardless of whether it could have succeeded entirely on its own. Same reasoning and pattern as
     * {@code DepartmentService.bulkDelete}.
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

    private void applyStatus(Designation designation, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            designation.activate();
        } else {
            designation.deactivate();
        }
    }

    /** skillCategoryId is optional (nullable) - only validated, never required, if supplied. */
    private void requireSkillCategoryExistsIfPresent(UUID skillCategoryId) {
        if (skillCategoryId != null && !skillCategories.existsById(skillCategoryId)) {
            throw new ValidationException("Unknown skill category id: " + skillCategoryId);
        }
    }

    private Set<Industry> resolveIndustries(Set<UUID> industryIds) {
        if (industryIds.isEmpty()) {
            throw new ValidationException("At least one industry must be selected.");
        }
        List<Industry> found = industries.findAllById(industryIds);
        if (found.size() != industryIds.size()) {
            Set<UUID> foundIds = new HashSet<>();
            found.forEach(i -> foundIds.add(i.getId()));
            List<UUID> unknown = industryIds.stream().filter(idValue -> !foundIds.contains(idValue)).toList();
            throw new ValidationException("Unknown industry id(s): " + unknown);
        }
        return new HashSet<>(found);
    }

    private Designation findOrThrow(UUID id) {
        return designations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Designation not found"));
    }

    private DesignationResponse toDto(Designation designation) {
        return new DesignationResponse(designation.getId(), designation.getName(), designation.getStatus(),
                images.urlOrNull(designation.getImageDocumentId()), industrySummariesOf(designation),
                designation.getSkillCategoryId());
    }

    private List<IndustrySummary> industrySummariesOf(Designation designation) {
        return designation.getIndustries().stream()
                .map(i -> new IndustrySummary(i.getId(), i.getName(), i.getCode()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }
}
