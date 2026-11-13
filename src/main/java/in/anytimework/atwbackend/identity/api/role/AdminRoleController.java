package in.anytimework.atwbackend.identity.api.role;

import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleResponse;
import in.anytimework.atwbackend.identity.api.role.dto.UpdateAdminRoleRequest;
import in.anytimework.atwbackend.identity.application.AdminRoleService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Roles",
        description = "Role CRUD and permission-set assignment, under `/api/v1/admin/roles/**`. Requires "
                + "`Authorization: Bearer` and the matching `admin_roles.*` permission (`view`/`create`/`edit`/`delete` "
                + "- 4 codes total).\n\n"
                + "**Privilege-escalation guard** (`PermissionEscalationGuard`, shared with Staff's role-assignment "
                + "check): a non-super-admin can never create or edit a role to grant a permission they don't "
                + "themselves currently hold. Super-admins are exempt (they implicitly hold every permission).\n\n"
                + "**System roles** (`systemRole: true`): protected from Update and Delete, but can still be "
                + "assigned to or removed from staff normally. The flag can only ever be set by seed data - no "
                + "endpoint here, including Create Role, can produce `systemRole: true`. Per the current seed "
                + "migration (`V4__identity.sql`), none of today's 4 legacy seed roles (Sub Admin, Relationship "
                + "Manager, Senior Developer, Junior Developer) are actually flagged as system roles - all 4 were "
                + "inserted with `is_system = FALSE` and nothing has updated that since, so right now every role, "
                + "seed or not, can be renamed/deleted like any other.")
public class AdminRoleController {

    private final AdminRoleService roleService;

    public AdminRoleController(AdminRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_roles.view')")
    @Operation(summary = "List all roles",
            description = "Returns every role with its full `permissionCodes` list. Not paginated - the role "
                    + "count is small and operator-managed, not user-generated, unlike Staff below.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All roles.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": [
                                {
                                  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                  "name": "Senior Developer",
                                  "description": "Full operational access across modules including configuration, payroll, reports and face enrollment.",
                                  "systemRole": false,
                                  "permissionCodes": ["admin_staff.view", "admin_staff.view_kyc", "admin_staff.view_bank_details"]
                                },
                                {
                                  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                  "name": "QA Tester",
                                  "description": "Read-only access for QA",
                                  "systemRole": false,
                                  "permissionCodes": ["dashboard.view"]
                                }
                              ],
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks `admin_roles.view`.",
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
    public ApiResponse<List<AdminRoleResponse>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.view')")
    @Operation(summary = "Get a role by id", description = "Returns one role with its full `permissionCodes` list.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role found.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                "name": "QA Tester",
                                "description": "Read-only access for QA",
                                "systemRole": false,
                                "permissionCodes": ["dashboard.view", "workers.view"]
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No role with this id.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Role not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:20:30Z",
                                "traceId": "b2c3d4e5f6071829"
                              }
                            }""")))
    })
    public ApiResponse<AdminRoleResponse> get(
            @Parameter(description = "Role id (UUID).", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('admin_roles.create')")
    @Operation(summary = "Create a role",
            description = "Checks run in this order: (1) `name` must not already be used by another role - "
                    + "`409 CONFLICT`; (2) every `permissionCodes` entry must exist in the catalog - "
                    + "`400 VALIDATION_ERROR` naming the unknown code(s); (3) unless the caller is a super-admin, "
                    + "`permissionCodes` must be entirely within the caller's own current permissions - "
                    + "`422 BUSINESS_RULE_VIOLATION`. Step 2 runs before step 3 deliberately, so a typo'd code "
                    + "reports as \"unknown\" rather than a confusing \"you don't hold this permission\" for a "
                    + "code that doesn't even exist. On success, creates a non-system role (`systemRole: false` "
                    + "always) and audits `ADMIN_ROLE_CREATED`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role created.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "name": "QA Tester",
                                "description": "Read-only access for QA",
                                "systemRole": false,
                                "permissionCodes": ["dashboard.view", "workers.view"]
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unknown permission code.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "VALIDATION_ERROR",
                                "message": "Unknown permission code(s): dashboard.viewx",
                                "fieldErrors": null,
                                "timestamp": "2026-10-24T10:21:10Z",
                                "traceId": "b2c3d4e5f6071829"
                              }
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Role name already in use.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "CONFLICT",
                                "message": "A role named 'QA Tester' already exists.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:21:00Z",
                                "traceId": "c3d4e5f607182930"
                              }
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Caller tried to grant a permission they don't hold themselves (not a super-admin).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "You cannot grant a permission you do not hold yourself.",
                                "fieldErrors": null,
                                "timestamp": "2026-10-24T10:21:45Z",
                                "traceId": "b2c3d4e5f607182a"
                              }
                            }""")))
    })
    public ApiResponse<AdminRoleResponse> create(@Valid @RequestBody CreateAdminRoleRequest request, HttpServletRequest http) {
        return ApiResponse.ok(roleService.create(request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.edit')")
    @Operation(summary = "Update a role",
            description = "Full replace of name, description, and the entire permission set - omitted codes are "
                    + "*removed* from the role, this is not a merge/patch. Checks run in this order: (1) role id "
                    + "must exist - `404`; (2) role must not be a system role - `422`; (3) `name` must not collide "
                    + "with a *different* role - `409`; (4) unknown permission code - `400`; (5) privilege-escalation, "
                    + "same rule as Create Role - `422`.\n\n**Non-obvious side effect:** every admin currently "
                    + "assigned this role keeps whatever permissions they had until their access token naturally "
                    + "expires or they make a new authenticated request - there is no proactive forced re-login "
                    + "here, unlike changing a *staff member's* role assignment (see Staff > Update Staff), which "
                    + "does force one.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "name": "QA Tester",
                                "description": "Updated description",
                                "systemRole": false,
                                "permissionCodes": ["dashboard.view"]
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Target is a system role.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "System roles cannot be modified.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:22:00Z",
                                "traceId": "d4e5f6071829303a"
                              }
                            }""")))
    })
    public ApiResponse<AdminRoleResponse> update(
            @Parameter(description = "Role id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminRoleRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(roleService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('admin_roles.delete')")
    @Operation(summary = "Delete a role",
            description = "Permanent - there is no soft-delete/archive for roles (contrast with Staff > Delete "
                    + "Staff below, which soft-deletes). Rejected with `404` if the id doesn't exist, `422` if it's "
                    + "a system role, or `409` if one or more staff members are still assigned to it - there is "
                    + "deliberately no cascade-reassignment; the caller must move affected staff to a different "
                    + "role first (via Staff > Update Staff), the safer default for an access-control resource.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role deleted.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "One or more staff members are still assigned to this role.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "CONFLICT",
                                "message": "Cannot delete role: 3 staff member(s) are currently assigned to it. Reassign them first.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:22:30Z",
                                "traceId": "e5f6071829303a4b"
                              }
                            }""")))
    })
    public ApiResponse<Void> delete(
            @Parameter(description = "Role id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            HttpServletRequest http) {
        roleService.delete(id, HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
