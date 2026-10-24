package in.anytimework.atwbackend.identity.api.role;

import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleDto;
import in.anytimework.atwbackend.identity.api.role.dto.UpdateAdminRoleRequest;
import in.anytimework.atwbackend.identity.application.AdminRoleService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Role CRUD and permission assignment. Every endpoint requires the matching {@code admin_roles.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/roles")
public class AdminRoleController {

    private final AdminRoleService roleService;

    public AdminRoleController(AdminRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_roles.view')")
    public ApiResponse<List<AdminRoleDto>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.view')")
    public ApiResponse<AdminRoleDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('admin_roles.create')")
    public ApiResponse<AdminRoleDto> create(@Valid @RequestBody CreateAdminRoleRequest request, HttpServletRequest http) {
        return ApiResponse.ok(roleService.create(request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.edit')")
    public ApiResponse<AdminRoleDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminRoleRequest request,
                                       HttpServletRequest http) {
        return ApiResponse.ok(roleService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        roleService.delete(id, HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
