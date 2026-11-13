package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.BulkUpdateMinimumWageRateStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateService;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Minimum-wage rate CRUD. No update/edit endpoint for the rate value itself - see
 * {@code MinimumWageRate}'s Javadoc; a rate change is always a new {@code create} with a new
 * {@code effectiveFrom}. Also deliberately has no delete/bulk-delete endpoint at all - see
 * {@code MinimumWageRateService}'s Javadoc for why deleting statutory history rows is never
 * appropriate here, unlike the delete-with-guards pattern used by other masterdata entities.
 * Every endpoint requires the matching {@code minimum_wage.*} permission.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/minimum-wage-rates")
@Tag(name = "Masterdata - Minimum Wage Rates", description = "The statutory minimum daily wage for a (state, "
        + "skill category) pair, effective from a given date. Rows are IMMUTABLE once created and this entity "
        + "has NO edit and NO delete/bulk-delete endpoint at all - a rate correction is always a brand-new "
        + "`create` carrying its own `effectiveFrom`, never an in-place edit, and a mis-entered row is retracted "
        + "via status (`POST /{id}/status` -> `INACTIVE`), never removed - this table is an append-only "
        + "statutory history needed to reconstruct exactly what rate legally applied on any past payroll date. "
        + "This is also the ONE masterdata entity that is paginated even on List: unlike "
        + "Department/Industry/Designation/Skill/Skill Category (small, bounded reference lists), this table "
        + "grows without bound as rates change over the years.")
public class AdminMinimumWageRateController {

    private final MinimumWageRateService minimumWageRateService;

    public AdminMinimumWageRateController(MinimumWageRateService minimumWageRateService) {
        this.minimumWageRateService = minimumWageRateService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('minimum_wage.view')")
    @Operation(summary = "List minimum wage rates", description = """
            Permission: `minimum_wage.view`.

            Paginated, multiselect-filterable, sortable list of rate rows (every version of every \
            state/skill-category pair, not just the currently-effective one - use `GET /effective` below to \
            resolve the single applicable rate as of a date). Default page size is 100, max 500. Default sort \
            is `effectiveFrom,desc`. Allowed `sort` values: `effectiveFrom`, `minDailyRate`, `status`, \
            `createdAt` - any other value returns 400 VALIDATION_ERROR. There is no free-text `search` here - a \
            rate row has no name/label of its own to search by; use the `stateIds`/`skillCategoryIds` filters \
            instead.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of rates",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MinimumWageRateResponse.class), examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  {
                                    "id": "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d",
                                    "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2",
                                    "stateName": "Rajasthan",
                                    "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a",
                                    "skillCategoryName": "Skilled",
                                    "minDailyRate": 650.00,
                                    "effectiveFrom": "2026-04-01",
                                    "status": "ACTIVE"
                                  }
                                ],
                                "page": 0, "size": 100, "totalElements": 1, "totalPages": 1, "first": true, "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` property",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Cannot sort by 'stateName'. Allowed fields: createdAt, effectiveFrom, minDailyRate, status", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<PageResponse<MinimumWageRateResponse>> list(
            @Parameter(description = "Return only rates for one of these states. Repeat the param for multiple values. Omit for every state.")
            @RequestParam(required = false) Set<UUID> stateIds,
            @Parameter(description = "Return only rates for one of these skill categories. Repeat the param for multiple values. Omit for every skill category.")
            @RequestParam(required = false) Set<UUID> skillCategoryIds,
            @Parameter(description = "Return only rates whose status is one of these values - e.g. only ACTIVE "
                    + "to see currently-live rows, or only INACTIVE to see retracted ones. Omit for every status.")
            @RequestParam(required = false) Set<ActiveStatus> statuses,
            @Parameter(description = "Standard Spring page/size/sort params - see this operation's description.")
            @PageableDefault(sort = "effectiveFrom", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(stateIds, skillCategoryIds, statuses), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('minimum_wage.view')")
    @Operation(summary = "Get a minimum wage rate by id", description = "Permission: `minimum_wage.view`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rate found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MinimumWageRateResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d", "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", "stateName": "Rajasthan", "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "skillCategoryName": "Skilled", "minDailyRate": 650.00, "effectiveFrom": "2026-04-01", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown rate id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Minimum wage rate not found", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<MinimumWageRateResponse> get(@Parameter(description = "Rate id.", required = true) @PathVariable UUID id) {
        return ApiResponse.ok(minimumWageRateService.get(id));
    }

    @GetMapping("/effective")
    @PreAuthorize("@authz.has('minimum_wage.view')")
    @Operation(summary = "Resolve the single rate in force for a state/skill-category as of a date", description = """
            Permission: `minimum_wage.view`.

            Resolves the one rate row that was legally in force for the given (state, skill category) pair as \
            of `asOf` - the latest `ACTIVE` row with `effectiveFrom <= asOf`. This is the endpoint payroll/wage \
            calculations should call, rather than listing every version and picking one client-side.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Effective rate resolved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MinimumWageRateResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d", "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", "stateName": "Rajasthan", "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "skillCategoryName": "Skilled", "minDailyRate": 650.00, "effectiveFrom": "2026-04-01", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No ACTIVE rate configured for this state/skill-category as of that date",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "No minimum wage rate configured for this state/skill-category as of 2024-01-01", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<MinimumWageRateResponse> effective(
            @Parameter(description = "State id.", required = true) @RequestParam UUID stateId,
            @Parameter(description = "Skill category id.", required = true) @RequestParam UUID skillCategoryId,
            @Parameter(description = "Date to resolve the rate as of, `yyyy-MM-dd`. Defaults to today (server clock) when omitted.", example = "2026-04-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(minimumWageRateService.resolveEffective(stateId, skillCategoryId, asOf));
    }

    @PostMapping
    @PreAuthorize("@authz.has('minimum_wage.create')")
    @Operation(summary = "Create a new minimum wage rate version", description = """
            Permission: `minimum_wage.create`.

            Always creates a new, independent, immutable version - there is no request type for editing an \
            existing rate's value. Validation: `stateId`/`skillCategoryId` required and must reference existing \
            rows; `minDailyRate` required, > 0, up to 8 integer and 2 fraction digits; `effectiveFrom` required.

            Business rule: an `ACTIVE` rate for this exact (`stateId`, `skillCategoryId`, `effectiveFrom`) \
            combination must not already exist. If this is a correction to a row entered in error, deactivate \
            the old row first (`POST /{id}/status`), then create the new version.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rate created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MinimumWageRateResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d", "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", "stateName": "Rajasthan", "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "skillCategoryName": "Skilled", "minDailyRate": 650.00, "effectiveFrom": "2026-04-01", "status": "ACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`stateId`/`skillCategoryId` does not exist, or a request-body field failed validation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Unknown state id: 00000000-0000-4000-8000-000000000000", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "An ACTIVE rate for this state/skill-category/effectiveFrom combination already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "CONFLICT", "message": "An active rate for this state/skill-category already exists effective from 2026-04-01. Deactivate it first if this is a correction, then create the new version.", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<MinimumWageRateResponse> create(@Valid @RequestBody CreateMinimumWageRateRequest request) {
        return ApiResponse.ok(minimumWageRateService.create(request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('minimum_wage.toggle_status')")
    @Operation(summary = "Set a rate's status", description = "Permission: `minimum_wage.toggle_status`. Explicit "
            + "target status, not a blind toggle. This is the ONLY way to retract a wrongly-entered rate - there "
            + "is no delete endpoint for this entity; see this tag's description.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MinimumWageRateResponse.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "id": "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d", "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", "stateName": "Rajasthan", "skillCategoryId": "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a", "skillCategoryName": "Skilled", "minDailyRate": 650.00, "effectiveFrom": "2026-04-01", "status": "INACTIVE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown rate id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "RESOURCE_NOT_FOUND", "message": "Minimum wage rate not found", "fieldErrors": null, "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<MinimumWageRateResponse> updateStatus(
            @Parameter(description = "Rate id.", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(minimumWageRateService.updateStatus(id, request.status()));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('minimum_wage.toggle_status')")
    @Operation(summary = "Set status for several rates at once", description = "Permission: "
            + "`minimum_wage.toggle_status`. E.g. retracting several wrongly-entered rates in one call. Partial "
            + "success is normal; check `data.failures` for any id that was skipped and why. No bulk-delete "
            + "counterpart exists - this entity never has a delete endpoint at all.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed (see `data.successCount` / `data.failures`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkActionResult.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "successCount": 1, "failures": [ { "id": "99999999-9999-4999-8999-999999999999", "reason": "Not found" } ] }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` was empty",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "ids: must not be empty", "fieldErrors": [ { "field": "ids", "message": "must not be empty" } ], "timestamp": "2026-11-01T10:25:00Z", "traceId": "303a4b5c6d071829" } }""")))
    })
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateMinimumWageRateStatusRequest request) {
        return ApiResponse.ok(minimumWageRateService.bulkUpdateStatus(request.ids(), request.status()));
    }
}
