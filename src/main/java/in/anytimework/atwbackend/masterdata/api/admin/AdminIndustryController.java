package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateIndustryStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.application.IndustrySearchCriteria;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
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

/** Industry CRUD + icon upload. Every endpoint requires the matching {@code industry.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/industries")
@Tag(name = "Masterdata - Industries", description = "The external sector a vacancy belongs to (e.g. "
        + "Construction, Manufacturing, Hospitality) - the top of the industry -> designation -> skill chain "
        + "vacancies are posted under. `code` is backend-generated (format `IND` + a 7-digit sequence, e.g. "
        + "`IND0000001`) and immutable - it is never accepted on create or update. Icon uploads are a separate "
        + "`PUT /{id}/image` (multipart, field name `file`) / `DELETE /{id}/image` pair, never a field on the "
        + "create/update body: allowed types are `image/png`, `image/jpeg`, `image/webp` only (no `image/svg+xml` "
        + "- an SVG can embed `<script>` and would be re-served byte-for-byte by blob storage), max 2MB, and the "
        + "declared Content-Type is cross-checked against the file's actual leading (magic) bytes. List never "
        + "resolves `imageUrl` (always `null`, even when an icon exists) - only `GET /{id}` resolves a real, "
        + "short-lived (~15 minute) signed URL; re-fetch rather than caching it.")
public class AdminIndustryController {

    private final IndustryService industryService;

    public AdminIndustryController(IndustryService industryService) {
        this.industryService = industryService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('industry.view')")
    @Operation(summary = "List industries", description = """
            Permission: `industry.view`.

            Paginated, multiselect-filterable, free-text-searchable, sortable list. Default page size is 100, \
            max 500 (`spring.data.web.pageable.default-page-size` / `.max-page-size`). Default sort is `name,asc`. \
            Allowed `sort` values: `name`, `status`, `createdAt` - any other value returns 400 VALIDATION_ERROR. \
            `imageUrl` is always `null` in this list response regardless of whether an icon was uploaded - \
            resolving a real signed URL per row would make an unbounded list endpoint slower as more rows gain \
            icons; call `GET /{id}` for the real value.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of industries",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null },
                                  { "id": "5e4d3c2b-1a0f-4e9d-8c7b-6a5f4e3d2c1b", "name": "Hospitality", "code": "IND0000002", "status": "ACTIVE", "imageUrl": null }
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
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Cannot sort by 'code'. Allowed fields: createdAt, name, status", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<PageResponse<IndustryResponse>> list(
            @Parameter(description = "Return only industries whose status is one of these values. Repeat the "
                    + "param for multiple values. Omit entirely to return every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Case-insensitive substring match against `name`. Omit to skip this filter.", example = "Constr")
            @RequestParam(required = false) String search,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description.")
            @PageableDefault(sort = "name") Pageable pageable) {
        return ApiResponse.ok(industryService.list(new IndustrySearchCriteria(statuses, search), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('industry.view')")
    @Operation(summary = "Get an industry by id", description = "Permission: `industry.view`. Unlike List, this "
            + "resolves a real `imageUrl` (a fresh ~15 minute signed URL) when an icon exists.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Industry found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown industry id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Industry not found", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<IndustryResponse> get(@Parameter(description = "Industry id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(industryService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('industry.create')")
    @Operation(summary = "Create an industry", description = """
            Permission: `industry.create`.

            Validation: `name` - required, non-blank, max 150 chars. There is no `code` field in the request - \
            it is generated server-side (a dedicated Postgres sequence, format `IND` + 7 digits) and returned in \
            the response; it is never reused and never accepted from the client.

            Business rule: an industry with this exact `name` must not already exist.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Industry created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "An industry with this name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "An industry named 'Construction' already exists.", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<IndustryResponse> create(@Valid @RequestBody CreateIndustryRequest request) {
        return ApiResponse.ok(industryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('industry.edit')")
    @Operation(summary = "Update an industry", description = """
            Permission: `industry.edit`.

            Same name validation and duplicate-name check as Create (excluding this industry's own current row). \
            There is still no `code` field here - it was assigned once at creation and is immutable thereafter.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Industry updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction & Real Estate", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown industry id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Industry not found", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<IndustryResponse> update(
            @Parameter(description = "Industry id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateIndustryRequest request) {
        return ApiResponse.ok(industryService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('industry.toggle_status')")
    @Operation(summary = "Set an industry's status", description = "Permission: `industry.toggle_status`. "
            + "Explicit target status, not a blind toggle.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown industry id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Industry not found", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<IndustryResponse> updateStatus(
            @Parameter(description = "Industry id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(industryService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('industry.toggle_status')")
    @Operation(summary = "Set status for several industries at once", description = "Permission: "
            + "`industry.toggle_status`. Partial success is normal - each id is processed independently; check "
            + "`data.failures` for any id that was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateIndustryStatusRequest request) {
        return ApiResponse.ok(industryService.bulkUpdateStatus(request.ids(), request.status()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('industry.edit')")
    @Operation(summary = "Upload/replace this industry's icon", description = """
            Permission: `industry.edit`.

            Multipart form-data with a single part, field name `file`. Checks, in order: (1) file must be \
            non-empty, (2) file must be <= 2MB, (3) declared `Content-Type` must be exactly `image/png`, \
            `image/jpeg`, or `image/webp`, (4) the file's actual leading bytes must match that declared type \
            (magic-byte check - catches, e.g., a `.pdf` renamed and declared as `image/png`). Uploading when an \
            icon already exists replaces it; the old blob is deleted. The returned `imageUrl` is a fresh, \
            short-lived signed URL.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon stored",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": "https://atwstorage.blob.core.windows.net/documents/industry-icon/6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c.png?sv=2025-05-05&se=2026-11-01T10%3A20%3A00Z&sr=b&sp=r&sig=REDACTED" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File content does not match the declared content type (or is empty/too large/unsupported)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "File content does not match declared content type image/png", "fieldErrors": null, "timestamp": "2026-11-01T10:06:00Z", "traceId": "c3d4e5f607182930" } }""")))
    })
    public ApiResponse<IndustryResponse> uploadImage(
            @Parameter(description = "Industry id.", required = true) @PathVariable UUID id,
            @Parameter(description = "Image file, multipart field name `file`. image/png, image/jpeg or image/webp only, max 2MB.", required = true)
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(industryService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('industry.edit')")
    @Operation(summary = "Remove this industry's icon", description = "Permission: `industry.edit`. Clears the "
            + "icon if any - not an error if there was none to begin with; this is a \"make sure there's no "
            + "image\" operation, not a \"delete this specific image\" one.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Icon cleared",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndustryResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", "name": "Construction", "code": "IND0000001", "status": "ACTIVE", "imageUrl": null }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown industry id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Industry not found", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<IndustryResponse> removeImage(@Parameter(description = "Industry id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(industryService.removeImage(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('industry.delete')")
    @Operation(summary = "Delete an industry", description = """
            Permission: `industry.delete`.

            A real, hard delete. Guarded by the existing foreign keys from workers and designations into this \
            table (`ON DELETE RESTRICT`) - deleting an industry that is still assigned to one or more workers or \
            designations is rejected with a friendly 409, not a raw database error. Reassign those first, or \
            deactivate the industry instead via `POST /{id}/status`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Industry deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown industry id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Industry not found", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Still assigned to one or more workers or designations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "Cannot delete this industry because it may still be assigned to one or more workers or designations. Reassign those first, or deactivate it instead.", "fieldErrors": null, "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<Void> delete(@Parameter(description = "Industry id.", required = true) @PathVariable UUID id) {
        industryService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('industry.delete')")
    @Operation(summary = "Delete several industries at once", description = "Permission: `industry.delete`. Same "
            + "FK guard as the single delete above, applied per id in its own transaction; check `data.failures` "
            + "for any id that was skipped and why.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "5e4d3c2b-1a0f-4e9d-8c7b-6a5f4e3d2c1b", "reason": "Cannot delete this industry because it may still be assigned to one or more workers or designations. Reassign those first, or deactivate it instead." } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:05:00Z", "traceId": "b2c3d4e5f6071829" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(industryService.bulkDelete(request.ids()));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
