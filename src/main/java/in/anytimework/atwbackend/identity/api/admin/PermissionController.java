package in.anytimework.atwbackend.identity.api.admin;

import in.anytimework.atwbackend.identity.api.admin.dto.PermissionGroup;
import in.anytimework.atwbackend.identity.api.admin.dto.PermissionGroup.PermissionItem;
import in.anytimework.atwbackend.identity.application.PermissionCatalogService;
import in.anytimework.atwbackend.identity.domain.AdminPermission;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exposes the permission catalog for the role editor. Requires the {@code admin_roles.view} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/permissions")
public class PermissionController {

    private final PermissionCatalogService catalogService;

    public PermissionController(PermissionCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_roles.view')")
    public ApiResponse<List<PermissionGroup>> catalog() {
        Map<String, List<PermissionItem>> byModule = new LinkedHashMap<>();
        for (AdminPermission p : catalogService.catalog()) {
            byModule.computeIfAbsent(p.getModule(), m -> new ArrayList<>())
                    .add(new PermissionItem(p.getCode(), p.getAction(), p.getLabel()));
        }
        List<PermissionGroup> groups = byModule.entrySet().stream()
                .map(e -> new PermissionGroup(e.getKey(), e.getValue()))
                .toList();
        return ApiResponse.ok(groups);
    }
}
