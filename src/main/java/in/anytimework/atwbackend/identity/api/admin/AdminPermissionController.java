package in.anytimework.atwbackend.identity.api.admin;

import in.anytimework.atwbackend.identity.api.admin.dto.PermissionGroup;
import in.anytimework.atwbackend.identity.api.admin.dto.PermissionGroup.PermissionItem;
import in.anytimework.atwbackend.identity.application.PermissionCatalogService;
import in.anytimework.atwbackend.identity.domain.AdminPermission;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Permissions",
        description = "Read-only permission catalog that backs the role editor's permission matrix (checkbox "
                + "grid). Not a CRUD resource - permissions are seeded by Flyway migrations, never created or "
                + "edited through this API. There are currently ~23 modules in the full catalog; `admin_roles` "
                + "(4 codes) and `admin_staff` (7 codes) are the two relevant to this collection.")
public class AdminPermissionController {

    private final PermissionCatalogService catalogService;

    public AdminPermissionController(PermissionCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_roles.view')")
    @Operation(
            summary = "List the full permission catalog",
            description = """
                    Returns every `admin_permission` row, grouped by module and ordered by module then action - \
                    this is the authoritative source for every permission code this API understands.

                    Each group is `{module, permissions: [{code, action, label}]}`. `code` (e.g. \
                    `"admin_staff.view_kyc"`, always `<module>.<action>`) is the exact string to use in \
                    Roles > Create/Update Role's `permissionCodes[]`, or to test membership in an admin's own \
                    `authorities[]` (see `GET /api/v1/admin/account/my-permissions`). `action` is just the \
                    verb suffix of `code`; `label` is a display-friendly form of `action`, currently always \
                    identical to it - kept as its own field so the catalog can diverge later without an API \
                    shape change. Use this response to render the checkbox grid a role editor needs, one row \
                    per module.""",
            tags = {"Admin Permissions"}
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Full catalog, grouped by module (excerpt below shows 2 of the ~23 modules).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": [
                                {
                                  "module": "admin_roles",
                                  "permissions": [
                                    { "code": "admin_roles.create", "action": "create", "label": "create" },
                                    { "code": "admin_roles.delete", "action": "delete", "label": "delete" },
                                    { "code": "admin_roles.edit", "action": "edit", "label": "edit" },
                                    { "code": "admin_roles.view", "action": "view", "label": "view" }
                                  ]
                                },
                                {
                                  "module": "admin_staff",
                                  "permissions": [
                                    { "code": "admin_staff.create", "action": "create", "label": "create" },
                                    { "code": "admin_staff.delete", "action": "delete", "label": "delete" },
                                    { "code": "admin_staff.edit", "action": "edit", "label": "edit" },
                                    { "code": "admin_staff.toggle_status", "action": "toggle_status", "label": "toggle_status" },
                                    { "code": "admin_staff.view", "action": "view", "label": "view" },
                                    { "code": "admin_staff.view_bank_details", "action": "view_bank_details", "label": "view_bank_details" },
                                    { "code": "admin_staff.view_kyc", "action": "view_kyc", "label": "view_kyc" }
                                  ]
                                }
                              ],
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller does not hold `admin_roles.view`.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "ACCESS_DENIED",
                                "message": "You do not have permission to perform this action",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:20:00Z",
                                "traceId": "a1b2c3d4e5f60718"
                              }
                            }""")))
    })
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
