package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateSkillCategoryStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.application.SkillCategorySearchCriteria;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
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

/** Skill-category CRUD (minimum-wage grouping only). Every endpoint requires the matching {@code skill_category.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/skill-categories")
@Tag(name = "Masterdata - Skill Categories", description = "Purely a minimum-wage grouping (e.g. 'Skilled', "
        + "'Semi-Skilled', 'Unskilled') - NOT part of the industry/designation/skill chain itself. The only "
        + "connection is one optional field on a Designation (`skillCategoryId`), which is what lets Minimum "
        + "Wage Rates resolve a rate for a given designation indirectly. No icon support. `code` is "
        + "backend-generated (format `SKC` + a 7-digit sequence, e.g. `SKC0000001`) and immutable - it is never "
        + "accepted on create or update.")
public class AdminSkillCategoryController {

    private final SkillCategoryService skillCategoryService;

    public AdminSkillCategoryController(SkillCategoryService skillCategoryService) {
        this.skillCategoryService = skillCategoryService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('skill_category.view')")
    @Operation(summary = "List skill categories", description = """
            Permission: `skill_category.view`.

            Paginated, multiselect-filterable, free-text-searchable, sortable list. Default page size is 100, \
            max 500. Default sort is `name,asc`. Allowed `sort` values: `name`, `status`, `createdAt` - any \
            other value returns 400 VALIDATION_ERROR.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of skill categories",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillCategoryResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  { "id": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "name": "Skilled", "code": "SKC0000001", "status": "ACTIVE" },
                                  { "id": "9d8c7b6a-5f4e-4d3c-8b2a-6f5e4d3c2b1a", "name": "Unskilled", "code": "SKC0000002", "status": "ACTIVE" }
                                ],
                                "page": 0, "size": 100, "totalElements": 2, "totalPages": 1, "first": true, "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` property",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Cannot sort by 'code'. Allowed fields: createdAt, name, status", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<PageResponse<SkillCategoryResponse>> list(
            @Parameter(description = "Return only skill categories whose status is one of these values. Omit for every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Case-insensitive substring match against `name`. Omit to skip this filter.", example = "Skill")
            @RequestParam(required = false) String search,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description.")
            @PageableDefault(sort = "name") Pageable pageable) {
        return ApiResponse.ok(skillCategoryService.list(new SkillCategorySearchCriteria(statuses, search), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('skill_category.view')")
    @Operation(summary = "Get a skill category by id", description = "Permission: `skill_category.view`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillCategoryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "name": "Skilled", "code": "SKC0000001", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill category id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill category not found", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<SkillCategoryResponse> get(@Parameter(description = "Skill category id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(skillCategoryService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('skill_category.create')")
    @Operation(summary = "Create a skill category", description = """
            Permission: `skill_category.create`.

            Validation: `name` - required, non-blank, max 150 chars. There is no `code` field in the request - \
            it is generated server-side (a dedicated Postgres sequence, format `SKC` + 7 digits) and returned in \
            the response; it is never reused and never accepted from the client.

            Business rule: a skill category with this exact `name` must not already exist.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillCategoryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "name": "Skilled", "code": "SKC0000001", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A skill category with this name already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "A skill category named 'Skilled' already exists.", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<SkillCategoryResponse> create(@Valid @RequestBody CreateSkillCategoryRequest request) {
        return ApiResponse.ok(skillCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('skill_category.edit')")
    @Operation(summary = "Update a skill category", description = """
            Permission: `skill_category.edit`.

            Same name validation and duplicate-name check as Create (excluding this row's own current values). \
            There is still no `code` field here - it was assigned once at creation and is immutable thereafter.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillCategoryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "name": "Skilled Labour", "code": "SKC0000001", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill category id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill category not found", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<SkillCategoryResponse> update(
            @Parameter(description = "Skill category id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateSkillCategoryRequest request) {
        return ApiResponse.ok(skillCategoryService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('skill_category.toggle_status')")
    @Operation(summary = "Set a skill category's status", description = "Permission: "
            + "`skill_category.toggle_status`. Explicit target status, not a blind toggle.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillCategoryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "name": "Skilled", "code": "SKC0000001", "status": "INACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill category id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill category not found", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<SkillCategoryResponse> updateStatus(
            @Parameter(description = "Skill category id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(skillCategoryService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('skill_category.toggle_status')")
    @Operation(summary = "Set status for several skill categories at once", description = "Permission: "
            + "`skill_category.toggle_status`. Partial success is normal; check `data.failures` for any id that "
            + "was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateSkillCategoryStatusRequest request) {
        return ApiResponse.ok(skillCategoryService.bulkUpdateStatus(request.ids(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('skill_category.delete')")
    @Operation(summary = "Delete a skill category", description = """
            Permission: `skill_category.delete`.

            A real, hard delete. Guarded by the existing foreign keys from designations and minimum wage rates \
            into this table (`ON DELETE RESTRICT`) - deleting a skill category that is still used by one or \
            more designations or minimum wage rates is rejected with a friendly 409. Deactivate it instead via \
            `POST /{id}/status` if it is still referenced.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category deleted",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill category id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill category not found", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Still used by designations or minimum wage rates",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "Cannot delete this skill category because it may still be used by designations or minimum wage rates. Deactivate it instead.", "fieldErrors": null, "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<Void> delete(@Parameter(description = "Skill category id.", required = true) @PathVariable UUID id) {
        skillCategoryService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('skill_category.delete')")
    @Operation(summary = "Delete several skill categories at once", description = "Permission: "
            + "`skill_category.delete`. Same FK guard as the single delete above, applied per id in its own "
            + "transaction; check `data.failures` for any id that was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "9d8c7b6a-5f4e-4d3c-8b2a-6f5e4d3c2b1a", "reason": "Cannot delete this skill category because it may still be used by designations or minimum wage rates. Deactivate it instead." } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:20:00Z", "traceId": "29303a4b5c6d0718" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(skillCategoryService.bulkDelete(request.ids()));
    }
}
