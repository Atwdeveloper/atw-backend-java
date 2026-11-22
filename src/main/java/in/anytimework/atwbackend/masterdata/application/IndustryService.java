package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * CRUD for industries. Authorization ({@code industry.view/create/edit/toggle_status/delete}) is
 * enforced at the controller via {@code @PreAuthorize}, matching every other admin-facing service in
 * this codebase - this service assumes its caller is already authorized.
 */
@Service
public class IndustryService {

    private static final String IMAGE_CATEGORY = "INDUSTRY_ICON";
    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final IndustryRepository industries;
    private final MasterDataImageSupport images;
    private final TransactionTemplate requiresNewTransaction;

    IndustryService(IndustryRepository industries, MasterDataImageSupport images, PlatformTransactionManager transactionManager) {
        this.industries = industries;
        this.images = images;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<IndustryResponse> list(IndustrySearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<Industry>> predicates = new ArrayList<>();
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(IndustrySpecifications.hasStatuses(criteria.statuses()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(IndustrySpecifications.nameContains(criteria.search()));
        }
        Page<Industry> page = industries.findAll(Specifications.allOf(predicates), pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public IndustryResponse get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public IndustryResponse create(CreateIndustryRequest request) {
        if (industries.existsByName(request.name())) {
            throw new ConflictException("An industry named '" + request.name() + "' already exists.");
        }
        Industry industry = new Industry(request.name(), generateIndustryCode());
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryResponse update(UUID id, UpdateIndustryRequest request) {
        Industry industry = findOrThrow(id);
        if (industries.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("An industry named '" + request.name() + "' already exists.");
        }
        industry.rename(request.name());
        return toDto(industries.save(industry));
    }

    /** Server-generated, never user-supplied - same mechanism as {@code Worker.workerCode}: a dedicated Postgres sequence, never reused, never a join key. */
    private String generateIndustryCode() {
        return "IND" + String.format("%07d", industries.nextIndustryCodeSequenceValue());
    }

    @Transactional
    public IndustryResponse updateStatus(UUID id, ActiveStatus status) {
        Industry industry = findOrThrow(id);
        applyStatus(industry, status);
        return toDto(industries.save(industry));
    }

    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, ActiveStatus status) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                Industry industry = findOrThrow(id);
                applyStatus(industry, status);
                industries.save(industry);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    @Transactional
    public void delete(UUID id) {
        Industry industry = findOrThrow(id);
        try {
            industries.delete(industry);
            industries.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this industry because it may still be assigned to one or more workers or "
                            + "designations. Reassign those first, or deactivate it instead.");
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

    @Transactional
    public IndustryResponse uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Industry industry = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, industry.getImageDocumentId(), filename, contentType, content);
        industry.setImageDocumentId(newImageId);
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryResponse removeImage(UUID id) {
        Industry industry = findOrThrow(id);
        images.remove(industry.getImageDocumentId());
        industry.setImageDocumentId(null);
        return toDto(industries.save(industry));
    }

    private void applyStatus(Industry industry, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            industry.activate();
        } else {
            industry.deactivate();
        }
    }

    private Industry findOrThrow(UUID id) {
        return industries.findById(id).orElseThrow(() -> new ResourceNotFoundException("Industry not found"));
    }

    private IndustryResponse toDto(Industry industry) {
        return new IndustryResponse(industry.getId(), industry.getName(), industry.getCode(), industry.getStatus(),
                images.urlOrNull(industry.getImageDocumentId()));
    }
}
