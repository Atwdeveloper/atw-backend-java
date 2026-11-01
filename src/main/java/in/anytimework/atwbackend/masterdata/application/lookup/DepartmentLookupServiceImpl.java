package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.Department;
import in.anytimework.atwbackend.masterdata.domain.DepartmentRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DepartmentLookupServiceImpl implements DepartmentLookupService {

    private final DepartmentRepository departments;

    DepartmentLookupServiceImpl(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Override
    public List<DepartmentSummary> list() {
        return departments.findAllByOrderByName().stream().map(DepartmentLookupServiceImpl::toSummary).toList();
    }

    @Override
    public DepartmentSummary get(UUID id) {
        return departments.findById(id).map(DepartmentLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return departments.existsById(id);
    }

    private static DepartmentSummary toSummary(Department department) {
        return new DepartmentSummary(department.getId(), department.getName());
    }
}
