package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.domain.Department;
import in.anytimework.atwbackend.masterdata.domain.DepartmentRepository;
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
 * CRUD for departments. Legacy left this unguarded (no RBAC module, no edit/delete at all) - that
 * was a gap, not a deliberate design, so this gets the same full treatment as every other masterdata
 * entity: authorization ({@code department.view/create/edit/toggle_status/delete}) enforced at the
 * controller, multiselect filtering + pagination, and guarded single/bulk delete.
 */
@Service
public class DepartmentService {

    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final DepartmentRepository departments;
    private final TransactionTemplate requiresNewTransaction;

    DepartmentService(DepartmentRepository departments, PlatformTransactionManager transactionManager) {
        this.departments = departments;
        // REQUIRES_NEW: each bulkDelete() attempt must commit/fail independently of the others - see
        // that method's Javadoc. Same pattern as DocumentServiceImpl's split transactions around a
        // blob upload.
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> list(DepartmentSearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<Department>> predicates = new ArrayList<>();
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            predicates.add(DepartmentSpecifications.hasStatuses(criteria.statuses()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(DepartmentSpecifications.nameContains(criteria.search()));
        }
        Page<Department> page = departments.findAll(Specifications.allOf(predicates), pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (departments.existsByName(request.name())) {
            throw new ConflictException("A department named '" + request.name() + "' already exists.");
        }
        Department department = new Department(request.name());
        return toDto(departments.save(department));
    }

    @Transactional
    public DepartmentResponse update(UUID id, UpdateDepartmentRequest request) {
        Department department = findOrThrow(id);
        if (departments.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A department named '" + request.name() + "' already exists.");
        }
        department.rename(request.name());
        return toDto(departments.save(department));
    }

    @Transactional
    public DepartmentResponse updateStatus(UUID id, ActiveStatus status) {
        Department department = findOrThrow(id);
        applyStatus(department, status);
        return toDto(departments.save(department));
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
                Department department = findOrThrow(id);
                applyStatus(department, status);
                departments.save(department);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    /**
     * Relies entirely on the existing {@code worker.department_id REFERENCES department(id) ON DELETE
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
        Department department = findOrThrow(id);
        try {
            departments.delete(department);
            departments.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Cannot delete this department because one or more workers are currently assigned to it. "
                            + "Reassign those workers to a different department first, or deactivate it instead.");
        }
    }

    /**
     * Deliberately NOT {@code @Transactional} itself - each id's delete runs in its OWN transaction via
     * {@link #requiresNewTransaction}, not one shared across the whole batch. Once a flush fails with
     * {@code DataIntegrityViolationException}, Hibernate marks that persistence context rollback-only
     * for the rest of ITS transaction, even though the exception is caught right here - so if this
     * method's own body ran inside one ambient transaction, the NEXT id in the loop would fail too,
     * regardless of whether it could have succeeded entirely on its own. Same reasoning and pattern as
     * {@code DocumentServiceImpl}'s split transactions around a blob upload.
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

    private void applyStatus(Department department, ActiveStatus status) {
        if (status == ActiveStatus.ACTIVE) {
            department.activate();
        } else {
            department.deactivate();
        }
    }

    private Department findOrThrow(UUID id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    private DepartmentResponse toDto(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getStatus());
    }
}
