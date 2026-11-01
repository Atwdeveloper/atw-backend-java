package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.AssignSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Designation CRUD + industry assignment + icon upload. Every endpoint requires the matching {@code designation.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/designations")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('designation.view')")
    public ApiResponse<List<DesignationDto>> list(@RequestParam(required = false) UUID industryId) {
        return ApiResponse.ok(designationService.list(industryId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('designation.view')")
    public ApiResponse<DesignationDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(designationService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('designation.create')")
    public ApiResponse<DesignationDto> create(@Valid @RequestBody CreateDesignationRequest request) {
        return ApiResponse.ok(designationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('designation.edit')")
    public ApiResponse<DesignationDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateDesignationRequest request) {
        return ApiResponse.ok(designationService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('designation.toggle_status')")
    public ApiResponse<DesignationDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(designationService.updateStatus(id, request.status()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('designation.edit')")
    public ApiResponse<DesignationDto> uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(designationService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('designation.edit')")
    public ApiResponse<DesignationDto> removeImage(@PathVariable UUID id) {
        return ApiResponse.ok(designationService.removeImage(id));
    }

    /** Separate from {@code update} on purpose - see DesignationService.assignSkillCategory's Javadoc. */
    @PutMapping("/{id}/skill-category")
    @PreAuthorize("@authz.has('designation.edit')")
    public ApiResponse<DesignationDto> assignSkillCategory(@PathVariable UUID id, @Valid @RequestBody AssignSkillCategoryRequest request) {
        return ApiResponse.ok(designationService.assignSkillCategory(id, request.skillCategoryId()));
    }

    @DeleteMapping("/{id}/skill-category")
    @PreAuthorize("@authz.has('designation.edit')")
    public ApiResponse<DesignationDto> clearSkillCategory(@PathVariable UUID id) {
        return ApiResponse.ok(designationService.clearSkillCategory(id));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
