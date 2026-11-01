package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Skill-category CRUD (minimum-wage grouping only). Every endpoint requires the matching {@code skill_category.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/skill-categories")
public class SkillCategoryController {

    private final SkillCategoryService skillCategoryService;

    public SkillCategoryController(SkillCategoryService skillCategoryService) {
        this.skillCategoryService = skillCategoryService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('skill_category.view')")
    public ApiResponse<List<SkillCategoryDto>> list() {
        return ApiResponse.ok(skillCategoryService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('skill_category.view')")
    public ApiResponse<SkillCategoryDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(skillCategoryService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('skill_category.create')")
    public ApiResponse<SkillCategoryDto> create(@Valid @RequestBody CreateSkillCategoryRequest request) {
        return ApiResponse.ok(skillCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('skill_category.edit')")
    public ApiResponse<SkillCategoryDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateSkillCategoryRequest request) {
        return ApiResponse.ok(skillCategoryService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('skill_category.toggle_status')")
    public ApiResponse<SkillCategoryDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(skillCategoryService.updateStatus(id, request.status()));
    }
}
