package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.Skill;
import in.anytimework.atwbackend.masterdata.domain.SkillRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class SkillLookupServiceImpl implements SkillLookupService {

    private final SkillRepository skills;

    SkillLookupServiceImpl(SkillRepository skills) {
        this.skills = skills;
    }

    @Override
    public List<SkillSummary> list() {
        return skills.findAllByOrderByName().stream().map(SkillLookupServiceImpl::toSummary).toList();
    }

    @Override
    public List<SkillSummary> listByDesignation(UUID designationId) {
        return skills.findByDesignationIdOrderByName(designationId).stream().map(SkillLookupServiceImpl::toSummary).toList();
    }

    @Override
    public SkillSummary get(UUID id) {
        return skills.findById(id).map(SkillLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return skills.existsById(id);
    }

    private static SkillSummary toSummary(Skill skill) {
        return new SkillSummary(skill.getId(), skill.getName(), skill.getDesignationId());
    }
}
