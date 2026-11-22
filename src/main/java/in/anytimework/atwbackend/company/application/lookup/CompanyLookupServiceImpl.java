package in.anytimework.atwbackend.company.application.lookup;

import in.anytimework.atwbackend.company.domain.Company;
import in.anytimework.atwbackend.company.domain.CompanyRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class CompanyLookupServiceImpl implements CompanyLookupService {

    private final CompanyRepository companies;

    CompanyLookupServiceImpl(CompanyRepository companies) {
        this.companies = companies;
    }

    @Override
    public CompanySummary get(UUID id) {
        return companies.findById(id).map(CompanyLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return companies.existsById(id);
    }

    private static CompanySummary toSummary(Company company) {
        return new CompanySummary(company.getId(), company.getCompanyCode(), company.getName(), company.getIndustryId());
    }
}
