package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.Skill;
import in.anytimework.atwbackend.masterdata.domain.SkillRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for skills, each belonging to exactly one designation. Authorization
 * ({@code skill.view/create/edit/toggle_status}) is enforced at the controller.
 */
@Service
public class SkillService {

    private static final String IMAGE_CATEGORY = "SKILL_ICON";

    private final SkillRepository skills;
    private final DesignationRepository designations;
    private final MasterDataImageSupport images;

    SkillService(SkillRepository skills, DesignationRepository designations, MasterDataImageSupport images) {
        this.skills = skills;
        this.designations = designations;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<SkillDto> list(UUID designationIdFilter) {
        List<Skill> found = designationIdFilter == null
                ? skills.findAllByOrderByName()
                : skills.findByDesignationIdOrderByName(designationIdFilter);
        // toListDto, not toDto: see IndustryService.list() for why image URLs aren't resolved per row here.
        return found.stream().map(SkillService::toListDto).toList();
    }

    @Transactional(readOnly = true)
    public SkillDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public SkillDto create(CreateSkillRequest request) {
        requireDesignationExists(request.designationId());
        if (skills.existsByDesignationIdAndName(request.designationId(), request.name())) {
            throw new ConflictException("A skill named '" + request.name() + "' already exists under this designation.");
        }
        Skill skill = new Skill(request.designationId(), request.name());
        return toDto(skills.save(skill));
    }

    @Transactional
    public SkillDto update(UUID id, UpdateSkillRequest request) {
        Skill skill = findOrThrow(id);
        requireDesignationExists(request.designationId());
        if (skills.existsByDesignationIdAndNameAndIdNot(request.designationId(), request.name(), id)) {
            throw new ConflictException("A skill named '" + request.name() + "' already exists under this designation.");
        }
        skill.update(request.designationId(), request.name());
        return toDto(skills.save(skill));
    }

    @Transactional
    public SkillDto updateStatus(UUID id, ActiveStatus status) {
        Skill skill = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            skill.activate();
        } else {
            skill.deactivate();
        }
        return toDto(skills.save(skill));
    }

    @Transactional
    public SkillDto uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Skill skill = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, skill.getImageDocumentId(), filename, contentType, content);
        skill.setImageDocumentId(newImageId);
        return toDto(skills.save(skill));
    }

    @Transactional
    public SkillDto removeImage(UUID id) {
        Skill skill = findOrThrow(id);
        images.remove(skill.getImageDocumentId());
        skill.setImageDocumentId(null);
        return toDto(skills.save(skill));
    }

    private void requireDesignationExists(UUID designationId) {
        if (!designations.existsById(designationId)) {
            throw new ValidationException("Unknown designation id: " + designationId);
        }
    }

    private Skill findOrThrow(UUID id) {
        return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    private SkillDto toDto(Skill skill) {
        return new SkillDto(skill.getId(), skill.getName(), skill.getDesignationId(), skill.getStatus(),
                images.urlOrNull(skill.getImageDocumentId()));
    }

    /** Same as {@link #toDto}, minus the per-row image URL resolution - see {@link #list(UUID)}. */
    private static SkillDto toListDto(Skill skill) {
        return new SkillDto(skill.getId(), skill.getName(), skill.getDesignationId(), skill.getStatus(), null);
    }
}
