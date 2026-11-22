package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class IndustryLookupServiceImpl implements IndustryLookupService {

    private final IndustryRepository industries;

    IndustryLookupServiceImpl(IndustryRepository industries) {
        this.industries = industries;
    }

    @Override
    public List<IndustrySummary> list() {
        return industries.findAllByOrderByName().stream().map(IndustryLookupServiceImpl::toSummary).toList();
    }

    @Override
    public IndustrySummary get(UUID id) {
        return industries.findById(id).map(IndustryLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Industry not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return industries.existsById(id);
    }

    private static IndustrySummary toSummary(Industry industry) {
        return new IndustrySummary(industry.getId(), industry.getName(), industry.getCode());
    }
}
