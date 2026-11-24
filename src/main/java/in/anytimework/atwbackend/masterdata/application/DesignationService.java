package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationIndustryRef;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationIndustry;
import in.anytimework.atwbackend.masterdata.domain.DesignationIndustryRepository;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for designations, including the designation-to-industry assignment. Authorization
 * ({@code designation.view/create/edit/toggle_status/delete}) is enforced at the controller.
 */
@Service
public class DesignationService {

    private static final String IMAGE_CATEGORY = "DESIGNATION_ICON";
    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");
    /** Primary first, then alphabetical - matches WorkerService.toResponse's sort for a worker's own designationRefs. */
    private static final Comparator<DesignationIndustryRef> INDUSTRY_REF_ORDER =
            Comparator.comparing(DesignationIndustryRef::primary).reversed()
                    .thenComparing(DesignationIndustryRef::name, String.CASE_INSENSITIVE_ORDER);

    private final DesignationRepository designations;
    private final DesignationIndustryRepository designationIndustries;
    private final IndustryRepository industries;
    private final SkillCategoryRepository skillCategories;
    private final MasterDataImageSupport images;
    private final TransactionTemplate requiresNewTransaction;

    DesignationService(DesignationRepository designations, DesignationIndustryRepository designationIndustries,
                        IndustryRepository industries, SkillCategoryRepository skillCategories,
                        MasterDataImageSupport images, PlatformTransactionManager transactionManager) {
        this.designations = designations;
        this.designationIndustries = designationIndustries;
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
        Map<UUID, String> skillCategoryNameById = skillCategoryNameById(page.getContent());
        Map<UUID, List<DesignationIndustryRef>> industryRefsByDesignationId = industryRefsByDesignationId(page.getContent());
        // Map.of() (the common case: no row on this page has a skill category) throws NPE on get(null)
        // unlike HashMap - d.getSkillCategoryId() is null for most designations, so this null-check
        // can't be folded into the map lookup itself.
        return PageResponse.from(page.map(d -> toDto(d,
                d.getSkillCategoryId() == null ? null : skillCategoryNameById.get(d.getSkillCategoryId()),
                industryRefsByDesignationId.getOrDefault(d.getId(), List.of()))));
    }

    /** Batched counterpart of {@link #skillCategoryNameOf} - one {@code WHERE id IN (...)} for the whole page, not one query per row. */
    private Map<UUID, String> skillCategoryNameById(List<Designation> rows) {
        Set<UUID> ids = rows.stream().map(Designation::getSkillCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillCategories.findAllById(ids).stream()
                .collect(Collectors.toMap(SkillCategory::getId, SkillCategory::getName));
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
        validateIndustrySelection(request.industryIds(), request.primaryIndustryId());
        requireSkillCategoryExistsIfPresent(request.skillCategoryId());
        Designation designation = new Designation(request.name());
        designation.assignSkillCategory(request.skillCategoryId());
        designation = designations.save(designation);
        saveIndustries(designation.getId(), request.industryIds(), request.primaryIndustryId());
        return toDto(designation);
    }

    @Transactional
    public DesignationResponse update(UUID id, UpdateDesignationRequest request) {
        // Pessimistic lock: serializes this full-replace against a concurrent one for the same
        // designation - see DesignationRepository.findByIdForUpdate's Javadoc for why a plain
        // findById isn't enough once uq_designation_industry_primary exists.
        Designation designation = findOrThrowForUpdate(id);
        if (designations.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A designation named '" + request.name() + "' already exists.");
        }
        validateIndustrySelection(request.industryIds(), request.primaryIndustryId());
        designation.rename(request.name());
        designation = designations.save(designation);
        designationIndustries.deleteByDesignationId(id);
        designationIndustries.flush();
        saveIndustries(id, request.industryIds(), request.primaryIndustryId());
        return toDto(designation);
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
            throw new ValidationException("Unknown skill category.");
        }
    }

    /** industryIds non-empty + all exist, plus primaryIndustryId must be one of them - mirrors WorkerService.validateDesignationSelection's shape. */
    private void validateIndustrySelection(Set<UUID> industryIds, UUID primaryIndustryId) {
        if (industryIds.isEmpty()) {
            throw new ValidationException("At least one industry must be selected.");
        }
        if (!industryIds.contains(primaryIndustryId)) {
            throw new ValidationException("primaryIndustryId must be one of industryIds.");
        }
        List<Industry> found = industries.findAllById(industryIds);
        if (found.size() != industryIds.size()) {
            throw new ValidationException("One or more selected industries could not be found.");
        }
    }

    private void saveIndustries(UUID designationId, Set<UUID> industryIds, UUID primaryIndustryId) {
        List<DesignationIndustry> rows = industryIds.stream()
                .map(industryId -> new DesignationIndustry(designationId, industryId, industryId.equals(primaryIndustryId)))
                .toList();
        designationIndustries.saveAll(rows);
    }

    private Designation findOrThrow(UUID id) {
        return designations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Designation not found"));
    }

    private Designation findOrThrowForUpdate(UUID id) {
        return designations.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Designation not found"));
    }

    /** Single-row resolution (get/create/update/...) - only {@link #list} needs the batched {@link #skillCategoryNameById}. */
    private String skillCategoryNameOf(Designation designation) {
        UUID skillCategoryId = designation.getSkillCategoryId();
        return skillCategoryId == null ? null
                : skillCategories.findById(skillCategoryId).map(SkillCategory::getName).orElse(null);
    }

    private DesignationResponse toDto(Designation designation) {
        return toDto(designation, skillCategoryNameOf(designation),
                industryRefsOf(designationIndustries.findByDesignationId(designation.getId())));
    }

    private DesignationResponse toDto(Designation designation, String skillCategoryName, List<DesignationIndustryRef> industryRefs) {
        return new DesignationResponse(designation.getId(), designation.getName(), designation.getStatus(),
                images.urlOrNull(designation.getImageDocumentId()), industryRefs,
                designation.getSkillCategoryId(), skillCategoryName);
    }

    private static DesignationIndustryRef toRef(DesignationIndustry link, Map<UUID, Industry> industryById) {
        Industry industry = industryById.get(link.getIndustryId());
        return new DesignationIndustryRef(industry.getId(), industry.getName(), industry.getCode(), link.isPrimary());
    }

    /** Single-row resolution - resolves just this one designation's own links' industries, nothing batched. */
    private List<DesignationIndustryRef> industryRefsOf(List<DesignationIndustry> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        Set<UUID> industryIds = links.stream().map(DesignationIndustry::getIndustryId).collect(Collectors.toSet());
        Map<UUID, Industry> industryById = industries.findAllById(industryIds).stream()
                .collect(Collectors.toMap(Industry::getId, i -> i));
        return links.stream().map(link -> toRef(link, industryById)).sorted(INDUSTRY_REF_ORDER).toList();
    }

    /** Batched counterpart of {@link #industryRefsOf} - one {@code WHERE designation_id IN (...)} and one {@code WHERE industry_id IN (...)} for the whole page, not one query per row. */
    private Map<UUID, List<DesignationIndustryRef>> industryRefsByDesignationId(List<Designation> rows) {
        Set<UUID> designationIds = rows.stream().map(Designation::getId).collect(Collectors.toSet());
        if (designationIds.isEmpty()) {
            return Map.of();
        }
        List<DesignationIndustry> links = designationIndustries.findByDesignationIdIn(designationIds);
        Set<UUID> industryIds = links.stream().map(DesignationIndustry::getIndustryId).collect(Collectors.toSet());
        Map<UUID, Industry> industryById = industries.findAllById(industryIds).stream()
                .collect(Collectors.toMap(Industry::getId, i -> i));
        Map<UUID, List<DesignationIndustryRef>> result = new HashMap<>();
        for (Map.Entry<UUID, List<DesignationIndustry>> entry
                : links.stream().collect(Collectors.groupingBy(DesignationIndustry::getDesignationId)).entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .map(link -> toRef(link, industryById)).sorted(INDUSTRY_REF_ORDER).toList());
        }
        return result;
    }
}
