package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.domain.SkillCategory;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for skill categories - purely a minimum-wage grouping, unrelated to the
 * industry/designation/skill chain. Authorization ({@code skill_category.view/create/edit/toggle_status})
 * is enforced at the controller.
 */
@Service
public class SkillCategoryService {

    private final SkillCategoryRepository skillCategories;

    SkillCategoryService(SkillCategoryRepository skillCategories) {
        this.skillCategories = skillCategories;
    }

    @Transactional(readOnly = true)
    public List<SkillCategoryDto> list() {
        return skillCategories.findAllByOrderByName().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SkillCategoryDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public SkillCategoryDto create(CreateSkillCategoryRequest request) {
        if (skillCategories.existsByName(request.name())) {
            throw new ConflictException("A skill category named '" + request.name() + "' already exists.");
        }
        if (skillCategories.existsByCode(request.code())) {
            throw new ConflictException("A skill category with code '" + request.code() + "' already exists.");
        }
        SkillCategory skillCategory = new SkillCategory(request.name(), request.code());
        return toDto(skillCategories.save(skillCategory));
    }

    @Transactional
    public SkillCategoryDto update(UUID id, UpdateSkillCategoryRequest request) {
        SkillCategory skillCategory = findOrThrow(id);
        if (skillCategories.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A skill category named '" + request.name() + "' already exists.");
        }
        if (skillCategories.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("A skill category with code '" + request.code() + "' already exists.");
        }
        skillCategory.rename(request.name(), request.code());
        return toDto(skillCategories.save(skillCategory));
    }

    @Transactional
    public SkillCategoryDto updateStatus(UUID id, ActiveStatus status) {
        SkillCategory skillCategory = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            skillCategory.activate();
        } else {
            skillCategory.deactivate();
        }
        return toDto(skillCategories.save(skillCategory));
    }

    private SkillCategory findOrThrow(UUID id) {
        return skillCategories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill category not found"));
    }

    private SkillCategoryDto toDto(SkillCategory skillCategory) {
        return new SkillCategoryDto(skillCategory.getId(), skillCategory.getName(), skillCategory.getCode(), skillCategory.getStatus());
    }
}
