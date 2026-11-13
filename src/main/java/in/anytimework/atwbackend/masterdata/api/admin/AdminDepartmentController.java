package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateDepartmentStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.application.DepartmentSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.Set;
import java.util.UUID;

/** Department CRUD. Every endpoint requires the matching {@code department.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/departments")
@Tag(name = "Masterdata - Departments", description = "ATW's own internal org structure (which team an admin "
        + "staff member belongs to) - entirely unrelated to the industry/designation/skill hierarchy vacancies "
        + "are posted under. No icon support. Legacy had zero RBAC and no edit/delete at all for this table; "
        + "this module gives it full CRUD, multiselect filtering, free-text search, sorting, and guarded "
        + "single/bulk delete, matching every other masterdata entity.")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('department.view')")
    @Operation(summary = "List departments", description = """
            Permission: `department.view`.

            Paginated, multiselect-filterable, free-text-searchable, sortable list of departments (active and \
            inactive). Default page size is 100 and the maximum is 500 \
            (`spring.data.web.pageable.default-page-size` / `spring.data.web.pageable.max-page-size` in \
            `application.properties`) - pass `?page=0&size=20` to override, up to the max; requesting more than \
            500 is capped at 500, not rejected.

            Default sort is `name,asc`. Allowed `sort` values: `name`, `status`, `createdAt` (e.g. \
            `?sort=createdAt,desc`) - any other property name returns 400 VALIDATION_ERROR before the query runs.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of departments",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  { "id": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d", "name": "Human Resources", "status": "ACTIVE" },
                                  { "id": "2b3c4d5e-6f7a-4b9c-8d2e-3f4a5b6c7d8e", "name": "Finance", "status": "ACTIVE" }
                                ],
                                "page": 0,
                                "size": 100,
                                "totalElements": 2,
                                "totalPages": 1,
                                "first": true,
                                "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` property",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "VALIDATION_ERROR",
                                "message": "Cannot sort by 'department_name'. Allowed fields: createdAt, name, status",
                                "fieldErrors": null,
                                "timestamp": "2026-11-01T10:00:00Z",
                                "traceId": "a1b2c3d4e5f60718"
                              }
                            }""")))
    })
    public ApiResponse<PageResponse<DepartmentResponse>> list(
            @Parameter(description = "Return only departments whose status is one of these values. Repeat the "
                    + "param for multiple values (`?statuses=ACTIVE&statuses=INACTIVE`). Omit entirely to return "
                    + "every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Case-insensitive substring match against `name`. Omit to skip this filter.", example = "Human")
            @RequestParam(required = false) String search,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description "
                    + "for the default size/sort and the allowed `sort` properties.")
            @PageableDefault(sort = "name") Pageable pageable) {
        return ApiResponse.ok(departmentService.list(new DepartmentSearchCriteria(statuses, search), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('department.view')")
    @Operation(summary = "Get a department by id", description = "Permission: `department.view`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d", "name": "Human Resources", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown department id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Department not found", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<DepartmentResponse> get(
            @Parameter(description = "Department id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(departmentService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('department.create')")
    @Operation(summary = "Create a department", description = """
            Permission: `department.create`.

            Validation: `name` - required, non-blank, max 150 chars.

            Business rule: a department with this exact `name` must not already exist (case-sensitive match on \
            the stored value) - checked application-side before insert, with a Postgres unique constraint as the \
            final safety net for a genuine race between two concurrent requests.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d", "name": "Human Resources", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A department with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "A department named 'Human Resources' already exists.", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('department.edit')")
    @Operation(summary = "Update a department", description = """
            Permission: `department.edit`.

            Same validation and duplicate-name check as Create, excluding this department's own current row \
            (so renaming "HR" back to "HR" is never a conflict with itself).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d", "name": "Human Resources & Admin", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown department id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Department not found", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<DepartmentResponse> update(
            @Parameter(description = "Department id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.ok(departmentService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('department.toggle_status')")
    @Operation(summary = "Set a department's status", description = """
            Permission: `department.toggle_status`.

            Takes an explicit target status rather than blindly toggling, so two quick calls can't race each \
            other into an unpredictable end state. There is no DELETE-by-deactivation here - see `DELETE /{id}` \
            below for a real delete, guarded by a foreign-key check.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d", "name": "Human Resources", "status": "INACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown department id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Department not found", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<DepartmentResponse> updateStatus(
            @Parameter(description = "Department id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(departmentService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('department.toggle_status')")
    @Operation(summary = "Set status for several departments at once", description = """
            Permission: `department.toggle_status`.

            Partial success is normal and expected: each id in the set is processed independently, so one \
            unknown id does not fail the rest of the batch. Check `data.failures` for which ids (if any) were \
            skipped and why.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "successCount": 1,
                                "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ]
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateDepartmentStatusRequest request) {
        return ApiResponse.ok(departmentService.bulkUpdateStatus(request.ids(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('department.delete')")
    @Operation(summary = "Delete a department", description = """
            Permission: `department.delete`.

            A real, hard delete - not a status flip. Guarded by the existing \
            `worker.department_id REFERENCES department(id) ON DELETE RESTRICT` foreign key: deleting a \
            department that still has one or more workers assigned to it is rejected with a friendly 409, not a \
            raw database error. Reassign those workers (or deactivate the department instead via \
            `POST /{id}/status`) before retrying the delete.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department deleted",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown department id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Department not found", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Still referenced by one or more workers",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "Cannot delete this department because one or more workers are currently assigned to it. Reassign those workers to a different department first, or deactivate it instead.", "fieldErrors": null, "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<Void> delete(@Parameter(description = "Department id.", required = true) @PathVariable UUID id) {
        departmentService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('department.delete')")
    @Operation(summary = "Delete several departments at once", description = """
            Permission: `department.delete`.

            Same FK guard as the single delete above, applied per id. Deliberately NOT one shared transaction \
            for the whole batch - each id commits or fails independently of the others, so a conflict on one id \
            can never roll back a sibling id's otherwise-successful delete. Check `data.failures` for which ids \
            (if any) were skipped and why (`"Not found"` or the same friendly FK-conflict message as the single \
            delete).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "successCount": 1,
                                "failures": [ { "id": "2b3c4d5e-6f7a-4b9c-8d2e-3f4a5b6c7d8e", "reason": "Cannot delete this department because one or more workers are currently assigned to it. Reassign those workers to a different department first, or deactivate it instead." } ]
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:00:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(departmentService.bulkDelete(request.ids()));
    }
}
