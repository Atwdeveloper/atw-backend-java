package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.domain.Designation;
import in.anytimework.atwbackend.masterdata.domain.DesignationRepository;
import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD for designations, including the designation-to-industry assignment. Authorization
 * ({@code designation.view/create/edit/toggle_status}) is enforced at the controller.
 */
@Service
public class DesignationService {

    private static final String IMAGE_CATEGORY = "DESIGNATION_ICON";

    private final DesignationRepository designations;
    private final IndustryRepository industries;
    private final SkillCategoryRepository skillCategories;
    private final MasterDataImageSupport images;

    DesignationService(DesignationRepository designations, IndustryRepository industries,
                        SkillCategoryRepository skillCategories, MasterDataImageSupport images) {
        this.designations = designations;
        this.industries = industries;
        this.skillCategories = skillCategories;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<DesignationDto> list(UUID industryIdFilter) {
        List<Designation> found = industryIdFilter == null
                ? designations.findAllByOrderByName()
                : designations.findByIndustries_IdOrderByName(industryIdFilter);
        // toListDto, not toDto: see IndustryService.list() for why image URLs aren't resolved per row here.
        return found.stream().map(this::toListDto).toList();
    }

    @Transactional(readOnly = true)
    public DesignationDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public DesignationDto create(CreateDesignationRequest request) {
        if (designations.existsByName(request.name())) {
            throw new ConflictException("A designation named '" + request.name() + "' already exists.");
        }
        Set<Industry> resolved = resolveIndustries(request.industryIds());
        requireSkillCategoryExistsIfPresent(request.skillCategoryId());
        Designation designation = new Designation(request.name());
        designation.replaceIndustries(resolved);
        designation.assignSkillCategory(request.skillCategoryId());
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationDto update(UUID id, UpdateDesignationRequest request) {
        Designation designation = findOrThrow(id);
        if (designations.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A designation named '" + request.name() + "' already exists.");
        }
        Set<Industry> resolved = resolveIndustries(request.industryIds());
        designation.rename(request.name());
        designation.replaceIndustries(resolved);
        return toDto(designations.save(designation));
    }

    /**
     * Separate from {@link #update}, same reasoning as {@link #uploadImage}/{@link #removeImage}
     * being their own endpoints: skillCategoryId is optional, so folding it into the general update
     * would make "the client didn't mention it" indistinguishable from "clear it", silently wiping a
     * previously-assigned category whenever a caller sends an update without that field.
     */
    @Transactional
    public DesignationDto assignSkillCategory(UUID id, UUID skillCategoryId) {
        Designation designation = findOrThrow(id);
        requireSkillCategoryExistsIfPresent(skillCategoryId);
        designation.assignSkillCategory(skillCategoryId);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationDto clearSkillCategory(UUID id) {
        Designation designation = findOrThrow(id);
        designation.assignSkillCategory(null);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationDto updateStatus(UUID id, ActiveStatus status) {
        Designation designation = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            designation.activate();
        } else {
            designation.deactivate();
        }
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationDto uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Designation designation = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, designation.getImageDocumentId(), filename, contentType, content);
        designation.setImageDocumentId(newImageId);
        return toDto(designations.save(designation));
    }

    @Transactional
    public DesignationDto removeImage(UUID id) {
        Designation designation = findOrThrow(id);
        images.remove(designation.getImageDocumentId());
        designation.setImageDocumentId(null);
        return toDto(designations.save(designation));
    }

    /** skillCategoryId is optional (nullable) - only validated, never required, if supplied. */
    private void requireSkillCategoryExistsIfPresent(UUID skillCategoryId) {
        if (skillCategoryId != null && !skillCategories.existsById(skillCategoryId)) {
            throw new ValidationException("Unknown skill category id: " + skillCategoryId);
        }
    }

    private Set<Industry> resolveIndustries(Set<UUID> industryIds) {
        if (industryIds.isEmpty()) {
            throw new ValidationException("At least one industry must be selected.");
        }
        List<Industry> found = industries.findAllById(industryIds);
        if (found.size() != industryIds.size()) {
            Set<UUID> foundIds = new HashSet<>();
            found.forEach(i -> foundIds.add(i.getId()));
            List<UUID> unknown = industryIds.stream().filter(idValue -> !foundIds.contains(idValue)).toList();
            throw new ValidationException("Unknown industry id(s): " + unknown);
        }
        return new HashSet<>(found);
    }

    private Designation findOrThrow(UUID id) {
        return designations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Designation not found"));
    }

    private DesignationDto toDto(Designation designation) {
        return new DesignationDto(designation.getId(), designation.getName(), designation.getStatus(),
                images.urlOrNull(designation.getImageDocumentId()), industrySummariesOf(designation),
                designation.getSkillCategoryId());
    }

    /** Same as {@link #toDto}, minus the per-row image URL resolution - see {@link #list(UUID)}. */
    private DesignationDto toListDto(Designation designation) {
        return new DesignationDto(designation.getId(), designation.getName(), designation.getStatus(),
                null, industrySummariesOf(designation), designation.getSkillCategoryId());
    }

    private List<IndustrySummary> industrySummariesOf(Designation designation) {
        return designation.getIndustries().stream()
                .map(i -> new IndustrySummary(i.getId(), i.getName(), i.getCode()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }
}
