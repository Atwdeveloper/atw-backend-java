package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.AssignSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateDesignationStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ValidationException;
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
import java.util.Set;
import java.util.UUID;

/** Designation CRUD + industry assignment + icon upload. Every endpoint requires the matching {@code designation.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/designations")
@Tag(name = "Masterdata - Designations", description = "A job title/role a vacancy is posted under (e.g. Mason, "
        + "Electrician) - belongs to one or more Industries, and optionally carries a Skill Category "
        + "classification used purely for Minimum Wage Rate lookups. Icon upload follows the identical rules "
        + "documented on the Industries tag. `skillCategoryId` is set/cleared through the dedicated "
        + "`PUT`/`DELETE /{id}/skill-category` endpoints, never through the general Update body - folding it "
        + "into Update would make \"caller didn't mention it\" indistinguishable from \"clear it\", silently "
        + "wiping a previously-assigned category on an unrelated rename.")
public class AdminDesignationController {

    private final DesignationService designationService;

    public AdminDesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('designation.view')")
    @Operation(summary = "List designations", description = """
            Permission: `designation.view`.

            Paginated, multiselect-filterable, free-text-searchable, sortable list. Default page size is 100, \
            max 500. Default sort is `name,asc`. Allowed `sort` values: `name`, `status`, `createdAt` - any \
            other value returns 400 VALIDATION_ERROR. `imageUrl` is always `null` here, same performance \
            rationale as Industries' list endpoint - call `GET /{id}` for the real value.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of designations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  {
                                    "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b",
                                    "name": "Mason",
                                    "status": "ACTIVE",
                                    "imageUrl": null,
                                    "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ],
                                    "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a"
                                  }
                                ],
                                "page": 0, "size": 100, "totalElements": 1, "totalPages": 1, "first": true, "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` property",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Cannot sort by 'industryName'. Allowed fields: createdAt, name, status", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<PageResponse<DesignationResponse>> list(
            @Parameter(description = "Return only designations belonging to at least one of these industries. "
                    + "Repeat the param for multiple values. Omit for every industry.")
            @RequestParam(required = false) Set<UUID> industryIds,
            @Parameter(description = "Return only designations assigned to one of these skill categories. "
                    + "Repeat the param for multiple values. Omit for every skill category (including designations "
                    + "with none assigned).")
            @RequestParam(required = false) Set<UUID> skillCategoryIds,
            @Parameter(description = "Return only designations whose status is one of these values. Omit for every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Case-insensitive substring match against `name`. Omit to skip this filter.", example = "Mason")
            @RequestParam(required = false) String search,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description.")
            @PageableDefault(sort = "name") Pageable pageable) {
        return ApiResponse.ok(designationService.list(
                new DesignationSearchCriteria(industryIds, skillCategoryIds, statuses, search), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('designation.view')")
    @Operation(summary = "Get a designation by id", description = "Permission: `designation.view`. Unlike List, "
            + "this resolves a real `imageUrl` when an icon exists.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Designation found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> get(@Parameter(description = "Designation id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(designationService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('designation.create')")
    @Operation(summary = "Create a designation", description = """
            Permission: `designation.create`.

            Validation, checked in this order: `name` required/non-blank/max 150 chars; `industryIds` required \
            and must contain at least one id (an empty array is rejected, not treated as "no industries yet"); \
            every id in `industryIds` must reference an existing industry; `skillCategoryId` is optional \
            (`null` is fine) but if provided must reference an existing skill category.

            Business rule: a designation with this exact `name` must not already exist (global uniqueness, \
            same as Department/Industry/Skill Category).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Designation created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`industryIds` was empty, or referenced an unknown industry/skill-category id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "At least one industry must be selected.", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A designation with this name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "A designation named 'Mason' already exists.", "fieldErrors": null, "timestamp": "2026-11-01T10:10:30Z", "traceId": "e5f6071829303a4b" } }""")))
    })
    public ApiResponse<DesignationResponse> create(@Valid @RequestBody CreateDesignationRequest request) {
        return ApiResponse.ok(designationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('designation.edit')")
    @Operation(summary = "Update a designation", description = """
            Permission: `designation.edit`.

            A full replace of `industryIds` (same "at least one, all must exist" validation as Create) plus \
            `name`. There is no `skillCategoryId` field in this body on purpose - see this tag's description; \
            whatever is currently assigned is left completely untouched no matter what you send here. Use \
            Assign/Clear Skill Category below to change it.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Designation updated (skillCategoryId carried over unchanged)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> update(
            @Parameter(description = "Designation id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDesignationRequest request) {
        return ApiResponse.ok(designationService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('designation.toggle_status')")
    @Operation(summary = "Set a designation's status", description = "Permission: `designation.toggle_status`. "
            + "Explicit target status, not a blind toggle.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> updateStatus(
            @Parameter(description = "Designation id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(designationService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('designation.toggle_status')")
    @Operation(summary = "Set status for several designations at once", description = "Permission: "
            + "`designation.toggle_status`. Partial success is normal; check `data.failures` for any id that was "
            + "skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateDesignationStatusRequest request) {
        return ApiResponse.ok(designationService.bulkUpdateStatus(request.ids(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('designation.delete')")
    @Operation(summary = "Delete a designation", description = """
            Permission: `designation.delete`.

            A real, hard delete. Guarded by the existing `worker_designation.designation_id` / \
            `skill.designation_id REFERENCES designation(id) ON DELETE RESTRICT` constraints - deleting a \
            designation that still has skills or workers assigned to it is rejected with a friendly 409. The \
            designation's own `designation_industry` join-table rows cascade and clean up on their own - that is \
            not something this guard applies to.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Designation deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Still has skills or workers assigned to it",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "Cannot delete this designation because it may still have skills or workers assigned to it. Reassign or remove those first, or deactivate it instead.", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<Void> delete(@Parameter(description = "Designation id.", required = true) @PathVariable UUID id) {
        designationService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('designation.delete')")
    @Operation(summary = "Delete several designations at once", description = "Permission: `designation.delete`. "
            + "Same FK guard as the single delete above, applied per id in its own transaction; check "
            + "`data.failures` for any id that was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "reason": "Cannot delete this designation because it may still have skills or workers assigned to it. Reassign or remove those first, or deactivate it instead." } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(designationService.bulkDelete(request.ids()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('designation.edit')")
    @Operation(summary = "Upload/replace this designation's icon", description = "Permission: `designation.edit`. "
            + "Identical validation/behavior to the Industries tag's icon upload (allow-list, magic-byte check, "
            + "2MB cap, replace-deletes-old, ~15 min signed URL) - see that tag's description for the full "
            + "rationale.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon stored",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": "https://atwstorage.blob.core.windows.net/documents/designation-icon/3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b.png?sv=2025-05-05&se=2026-11-01T10%3A30%3A00Z&sr=b&sp=r&sig=REDACTED", "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File content does not match the declared content type (or is empty/too large/unsupported)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "File content does not match declared content type image/png", "fieldErrors": null, "timestamp": "2026-11-01T10:06:00Z", "traceId": "c3d4e5f607182930" } }""")))
    })
    public ApiResponse<DesignationResponse> uploadImage(
            @Parameter(description = "Designation id.", required = true) @PathVariable UUID id,
            @Parameter(description = "Image file, multipart field name `file`. image/png, image/jpeg or image/webp only, max 2MB.", required = true)
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(designationService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('designation.edit')")
    @Operation(summary = "Remove this designation's icon", description = "Permission: `designation.edit`. Clears "
            + "the icon if any - not an error if there was none.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon cleared",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> removeImage(@Parameter(description = "Designation id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(designationService.removeImage(id));
    }

    /** Separate from {@code update} on purpose - see DesignationService.assignSkillCategory's Javadoc. */
    @PutMapping("/{id}/skill-category")
    @PreAuthorize("@authz.has('designation.edit')")
    @Operation(summary = "Assign (or replace) this designation's skill category", description = """
            Permission: `designation.edit`.

            Sets or replaces this designation's skill-category classification - see this tag's description for \
            why this is a separate endpoint from Update. `skillCategoryId` is required here (unlike on Create, \
            where it is optional) - to clear it instead, use Clear Skill Category below, not this endpoint with \
            a null body.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category assigned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`skillCategoryId` does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Unknown skill category id: 00000000-0000-4000-8000-000000000000", "fieldErrors": null, "timestamp": "2026-11-01T10:11:00Z", "traceId": "f60718293a4b5c6d" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> assignSkillCategory(
            @Parameter(description = "Designation id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignSkillCategoryRequest request) {
        return ApiResponse.ok(designationService.assignSkillCategory(id, request.skillCategoryId()));
    }

    @DeleteMapping("/{id}/skill-category")
    @PreAuthorize("@authz.has('designation.edit')")
    @Operation(summary = "Clear this designation's skill category", description = "Permission: "
            + "`designation.edit`. No request body. Sets `skillCategoryId` back to `null`; not an error if it "
            + "was already unset.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill category cleared",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = DesignationResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "name": "Mason (Skilled)", "status": "ACTIVE", "imageUrl": null, "industries": [ { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001" } ], "skillCategoryId": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown designation id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Designation not found", "fieldErrors": null, "timestamp": "2026-11-01T10:10:00Z", "traceId": "d4e5f6071829303a" } }""")))
    })
    public ApiResponse<DesignationResponse> clearSkillCategory(@Parameter(description = "Designation id.", required = true) @PathVariable UUID id) {
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
