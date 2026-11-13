package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateSkillStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.application.SkillSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.SkillService;
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

/** Skill CRUD + icon upload. Every endpoint requires the matching {@code skill.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/skills")
@Tag(name = "Masterdata - Skills", description = "A specific skill belonging to exactly one Designation (e.g. "
        + "'Brick Laying' under Mason). Icon upload follows the identical rules documented on the Industries tag. "
        + "Uniqueness is scoped PER DESIGNATION, not global - unlike every other 'name must be unique' rule in "
        + "this module (Department/Industry/Designation/Skill Category all check uniqueness across the whole "
        + "table), the same skill name can legitimately exist under two different designations (e.g. 'Measuring' "
        + "under both Mason and Carpenter).")
public class AdminSkillController {

    private final SkillService skillService;

    public AdminSkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('skill.view')")
    @Operation(summary = "List skills", description = """
            Permission: `skill.view`.

            Paginated, multiselect-filterable, free-text-searchable, sortable list. Default page size is 100, \
            max 500. Default sort is `name,asc`. Allowed `sort` values: `name`, `status`, `createdAt` - notably \
            `designationName` is NOT sortable (it is resolved in a batched lookup after the page is fetched, not \
            a real column on the skill table); any unsupported value returns 400 VALIDATION_ERROR. `imageUrl` is \
            always `null` here, same performance rationale as the Industries tag - call `GET /{id}` for the real \
            value.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of skills",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": null }
                                ],
                                "page": 0, "size": 100, "totalElements": 1, "totalPages": 1, "first": true, "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` property",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Cannot sort by 'designationName'. Allowed fields: createdAt, name, status", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<PageResponse<SkillResponse>> list(
            @Parameter(description = "Return only skills belonging to one of these designations. Repeat the "
                    + "param for multiple values. Omit for every designation.")
            @RequestParam(required = false) Set<UUID> designationIds,
            @Parameter(description = "Return only skills whose status is one of these values. Omit for every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Case-insensitive substring match against `name`. Omit to skip this filter.", example = "Brick")
            @RequestParam(required = false) String search,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description.")
            @PageableDefault(sort = "name") Pageable pageable) {
        return ApiResponse.ok(skillService.list(new SkillSearchCriteria(designationIds, statuses, search), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('skill.view')")
    @Operation(summary = "Get a skill by id", description = "Permission: `skill.view`. Unlike List, this resolves "
            + "a real `imageUrl` when an icon exists.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill not found", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<SkillResponse> get(@Parameter(description = "Skill id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(skillService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('skill.create')")
    @Operation(summary = "Create a skill", description = """
            Permission: `skill.create`.

            Validation, checked in this order: `designationId` required, must reference an existing designation; \
            `name` required, non-blank, max 150 chars.

            Business rule: a skill with this exact `name` must not already exist under this same `designationId` \
            - uniqueness is per-designation here, not global; see this tag's description.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`designationId` does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Unknown designation id: 00000000-0000-4000-8000-000000000000", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A skill with this name already exists under this designation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "A skill named 'Brick Laying' already exists under this designation.", "fieldErrors": null, "timestamp": "2026-11-01T10:15:30Z", "traceId": "1829303a4b5c6d07" } }""")))
    })
    public ApiResponse<SkillResponse> create(@Valid @RequestBody CreateSkillRequest request) {
        return ApiResponse.ok(skillService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('skill.edit')")
    @Operation(summary = "Update a skill", description = """
            Permission: `skill.edit`.

            Same validation as Create, including `designationId` - a skill CAN be moved to a different \
            designation here (the request requires a `designationId`, not just a `name`). The duplicate-name \
            check runs against the `designationId` in THIS request body (i.e. the target designation after the \
            move, not the skill's current one).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying (Advanced)", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill not found", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<SkillResponse> update(
            @Parameter(description = "Skill id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateSkillRequest request) {
        return ApiResponse.ok(skillService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('skill.toggle_status')")
    @Operation(summary = "Set a skill's status", description = "Permission: `skill.toggle_status`. Explicit "
            + "target status, not a blind toggle.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "INACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill not found", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<SkillResponse> updateStatus(
            @Parameter(description = "Skill id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(skillService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('skill.toggle_status')")
    @Operation(summary = "Set status for several skills at once", description = "Permission: "
            + "`skill.toggle_status`. Partial success is normal; check `data.failures` for any id that was "
            + "skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateSkillStatusRequest request) {
        return ApiResponse.ok(skillService.bulkUpdateStatus(request.ids(), request.status()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('skill.edit')")
    @Operation(summary = "Upload/replace this skill's icon", description = "Permission: `skill.edit`. Identical "
            + "validation/behavior to the Industries tag's icon upload (allow-list, magic-byte check, 2MB cap, "
            + "replace-deletes-old, ~15 min signed URL) - see that tag's description for the full rationale.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon stored",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": "https://atwstorage.blob.core.windows.net/documents/skill-icon/5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a.png?sv=2025-05-05&se=2026-11-01T10%3A40%3A00Z&sr=b&sp=r&sig=REDACTED" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File content does not match the declared content type (or is empty/too large/unsupported)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "File content does not match declared content type image/png", "fieldErrors": null, "timestamp": "2026-11-01T10:06:00Z", "traceId": "c3d4e5f607182930" } }""")))
    })
    public ApiResponse<SkillResponse> uploadImage(
            @Parameter(description = "Skill id.", required = true) @PathVariable UUID id,
            @Parameter(description = "Image file, multipart field name `file`. image/png, image/jpeg or image/webp only, max 2MB.", required = true)
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(skillService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('skill.edit')")
    @Operation(summary = "Remove this skill's icon", description = "Permission: `skill.edit`. Clears the icon if "
            + "any - not an error if there was none.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon cleared",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SkillResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "name": "Brick Laying", "designationId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "designationName": "Mason", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill not found", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<SkillResponse> removeImage(@Parameter(description = "Skill id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(skillService.removeImage(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('skill.delete')")
    @Operation(summary = "Delete a skill", description = """
            Permission: `skill.delete`.

            A real, hard delete. Guarded by the existing `worker_skill.skill_id REFERENCES skill(id) ON DELETE \
            RESTRICT` constraint - deleting a skill that is still assigned to one or more workers is rejected \
            with a friendly 409. Remove it from those workers first, or deactivate it instead via \
            `POST /{id}/status`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Skill deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown skill id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Skill not found", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Still assigned to one or more workers",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "Cannot delete this skill because it may still be assigned to workers. Remove it from those workers first, or deactivate it instead.", "fieldErrors": null, "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<Void> delete(@Parameter(description = "Skill id.", required = true) @PathVariable UUID id) {
        skillService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('skill.delete')")
    @Operation(summary = "Delete several skills at once", description = "Permission: `skill.delete`. Same FK "
            + "guard as the single delete above, applied per id in its own transaction; check `data.failures` "
            + "for any id that was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "reason": "Cannot delete this skill because it may still be assigned to workers. Remove it from those workers first, or deactivate it instead." } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:15:00Z", "traceId": "071829303a4b5c6d" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(skillService.bulkDelete(request.ids()));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
