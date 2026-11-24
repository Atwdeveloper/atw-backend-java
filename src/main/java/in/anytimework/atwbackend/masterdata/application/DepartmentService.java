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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD for departments. Legacy left this unguarded (no RBAC module, no edit/delete at all) - that
 * was a gap, not a deliberate design, so this gets the same full treatment as every other masterdata
 * entity: authorization ({@code department.view/create/edit/toggle_status/delete}) enforced at the
 * controller, multiselect filtering + pagination, and single/bulk delete. Delete has no FK guard to
 * worry about - {@code worker.department_id} (the only column that ever referenced this table) was
 * removed entirely (see {@code V40__worker_remove_department.sql}), and nothing else does.
 */
@Service
public class DepartmentService {

    /** Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "status", "createdAt");

    private final DepartmentRepository departments;

    DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
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

    @Transactional
    public void delete(UUID id) {
        Department department = findOrThrow(id);
        departments.delete(department);
    }

    /**
     * Mirrors {@link #bulkUpdateStatus}'s reasoning, not {@code WorkerService.bulkDelete}'s - a plain
     * delete with nothing referencing this table by FK can only ever fail with a clean, pre-write
     * {@link ResourceNotFoundException}, never a failed flush, so every id safely shares one ordinary
     * transaction instead of needing its own.
     */
    @Transactional
    public BulkActionResult bulkDelete(Set<UUID> ids) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                delete(id);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
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
