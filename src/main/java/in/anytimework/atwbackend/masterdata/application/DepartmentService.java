package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.domain.Department;
import in.anytimework.atwbackend.masterdata.domain.DepartmentRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for departments. Legacy left this unguarded (no RBAC module, no edit/delete at all) - that
 * was a gap, not a deliberate design, so this gets the same full treatment as the other three:
 * authorization ({@code department.view/create/edit/toggle_status}) enforced at the controller.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departments;

    DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> list() {
        return departments.findAllByOrderByName().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public DepartmentDto create(CreateDepartmentRequest request) {
        if (departments.existsByName(request.name())) {
            throw new ConflictException("A department named '" + request.name() + "' already exists.");
        }
        Department department = new Department(request.name());
        return toDto(departments.save(department));
    }

    @Transactional
    public DepartmentDto update(UUID id, UpdateDepartmentRequest request) {
        Department department = findOrThrow(id);
        if (departments.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A department named '" + request.name() + "' already exists.");
        }
        department.rename(request.name());
        return toDto(departments.save(department));
    }

    @Transactional
    public DepartmentDto updateStatus(UUID id, ActiveStatus status) {
        Department department = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            department.activate();
        } else {
            department.deactivate();
        }
        return toDto(departments.save(department));
    }

    private Department findOrThrow(UUID id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    private DepartmentDto toDto(Department department) {
        return new DepartmentDto(department.getId(), department.getName(), department.getStatus());
    }
}
