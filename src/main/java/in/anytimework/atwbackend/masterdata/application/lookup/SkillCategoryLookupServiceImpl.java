package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class SkillCategoryLookupServiceImpl implements SkillCategoryLookupService {

    private final SkillCategoryRepository skillCategories;

    SkillCategoryLookupServiceImpl(SkillCategoryRepository skillCategories) {
        this.skillCategories = skillCategories;
    }

    @Override
    public List<SkillCategorySummary> list() {
        return skillCategories.findAllByOrderByName().stream().map(SkillCategoryLookupServiceImpl::toSummary).toList();
    }

    @Override
    public SkillCategorySummary get(UUID id) {
        return skillCategories.findById(id).map(SkillCategoryLookupServiceImpl::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Skill category not found"));
    }

    @Override
    public boolean exists(UUID id) {
        return skillCategories.existsById(id);
    }

    private static SkillCategorySummary toSummary(SkillCategory skillCategory) {
        return new SkillCategorySummary(skillCategory.getId(), skillCategory.getName(), skillCategory.getCode());
    }
}
