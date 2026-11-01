package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.application.SkillService;
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

/** Skill CRUD + icon upload. Every endpoint requires the matching {@code skill.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('skill.view')")
    public ApiResponse<List<SkillDto>> list(@RequestParam(required = false) UUID designationId) {
        return ApiResponse.ok(skillService.list(designationId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('skill.view')")
    public ApiResponse<SkillDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(skillService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('skill.create')")
    public ApiResponse<SkillDto> create(@Valid @RequestBody CreateSkillRequest request) {
        return ApiResponse.ok(skillService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('skill.edit')")
    public ApiResponse<SkillDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateSkillRequest request) {
        return ApiResponse.ok(skillService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('skill.toggle_status')")
    public ApiResponse<SkillDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(skillService.updateStatus(id, request.status()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('skill.edit')")
    public ApiResponse<SkillDto> uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(skillService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('skill.edit')")
    public ApiResponse<SkillDto> removeImage(@PathVariable UUID id) {
        return ApiResponse.ok(skillService.removeImage(id));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
