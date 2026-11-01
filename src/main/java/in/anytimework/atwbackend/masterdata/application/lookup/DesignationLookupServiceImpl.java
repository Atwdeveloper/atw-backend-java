package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DesignationLookupServiceImpl implements DesignationLookupService {

    private final DesignationRepository designations;

    DesignationLookupServiceImpl(DesignationRepository designations) {
        this.designations = designations;
    }

    @Override
    public List<DesignationSummary> list() {
        return designations.findAllByOrderByName().stream().map(DesignationLookupServiceImpl::toSummary).toList();
    }

    @Override
    public DesignationSummary get(UUID id) {
        return designations.findById(id).map(DesignationLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Designation not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return designations.existsById(id);
    }

    private static DesignationSummary toSummary(Designation designation) {
        return new DesignationSummary(designation.getId(), designation.getName());
    }
}
