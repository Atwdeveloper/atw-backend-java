package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
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

/** Department CRUD. Every endpoint requires the matching {@code department.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('department.view')")
    public ApiResponse<List<DepartmentDto>> list() {
        return ApiResponse.ok(departmentService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('department.view')")
    public ApiResponse<DepartmentDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(departmentService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('department.create')")
    public ApiResponse<DepartmentDto> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('department.edit')")
    public ApiResponse<DepartmentDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('department.toggle_status')")
    public ApiResponse<DepartmentDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(departmentService.updateStatus(id, request.status()));
    }
}
