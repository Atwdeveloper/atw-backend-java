package in.anytimework.atwbackend.company.api.admin;

import in.anytimework.atwbackend.company.api.admin.dto.BulkUpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyAddressResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyExportRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyOwnerRef;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyStatusHistoryResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanySummaryResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CreateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.application.CompanyAddressInput;
import in.anytimework.atwbackend.company.application.CompanyAddressService;
import in.anytimework.atwbackend.company.application.CompanyAddressView;
import in.anytimework.atwbackend.company.application.CompanyOwnerView;
import in.anytimework.atwbackend.company.application.CompanySearchCriteria;
import in.anytimework.atwbackend.company.application.CompanyService;
import in.anytimework.atwbackend.company.application.CompanyStatusHistoryView;
import in.anytimework.atwbackend.company.application.CompanySummaryView;
import in.anytimework.atwbackend.company.application.CreateCompanyCommand;
import in.anytimework.atwbackend.company.application.UpdateCompanyAddressCommand;
import in.anytimework.atwbackend.company.application.UpdateCompanyCommand;
import in.anytimework.atwbackend.company.application.CompanyView;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import in.anytimework.atwbackend.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Company CRUD, approval-status lifecycle, GST-verification triggering, and the address sub-resource -
 * the ATW-admin-facing side of the company module. Every mutating endpoint requires the matching
 * {@code company.*} permission, checked server-side via {@code @PreAuthorize("@authz.has(...)")"} - the
 * same real, per-endpoint check every other {@code Admin*Controller} in this codebase already applies,
 * and the exact gap the locked spec for this module calls out as the single biggest bug in the legacy
 * reference app (almost no admin company action there checked a permission code at all).
 *
 * <p>Deliberately NOT in this controller / this round (confirmed out of scope - see
 * {@code CompanyService}'s class Javadoc): company documents, the company-user self-service
 * login/auth flow (accept-invite, login, OTP, refresh, etc. - a different, not-yet-built actor type),
 * staff-user creation beyond the single OWNER row, and custom per-company permission profiles.</p>
 */
@Tag(name = "Admin - Companies", description = "Company CRUD, approval-status lifecycle, GST-verification "
        + "triggering, and the address sub-resource. Every mutating endpoint requires the matching "
        + "company.* permission. Company creation always provisions exactly one OWNER account and sends "
        + "it an invite email, but accepting that invite (the company-user login flow itself) is a "
        + "separate, not-yet-built module slice - creating a company here never depends on it.")
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/companies")
public class AdminCompanyController {

    private final CompanyService companyService;
    private final CompanyAddressService addressService;

    public AdminCompanyController(CompanyService companyService, CompanyAddressService addressService) {
        this.companyService = companyService;
        this.addressService = addressService;
    }

    @Operation(summary = "List companies", description = """
            **Permission:** `company.view`.

            Paginated, multiselect-filterable (`status`, `industryId`, `stateId`, `cityId`), \
            free-text-searchable (`search` matches `name` or `companyCode`) list, returned as the \
            lighter `CompanySummaryResponse` (no owner, no addresses - see Get Company for those). \
            Default sort is `createdAt,desc`. Allowed `sort` values: `name`, `companyCode`, `status`, \
            `createdAt` - none of the filter-only fields below (`gstVerified`, `createdFrom`/`createdTo`, \
            `stateId`/`cityId`) are sortable columns.

            **Filters** - every one is optional and independent; a non-empty multiselect filter matches \
            ANY of its own values (an OR within that filter), and every non-empty filter is ANDed \
            together with the others.""")
    @Parameter(name = "status", description = "Filter by one or more approval statuses. Omit for every status.")
    @Parameter(name = "industryId", description = "Filter by one or more industry ids (Masterdata module). Omit for every industry.")
    @Parameter(name = "search", description = "Case-insensitive substring match against name or company code.", example = "Acme")
    @Parameter(name = "gstVerified", description = "true -> GST-verified companies only, false -> unverified only, omit for both.")
    @Parameter(name = "createdFrom", description = "Created on or after this date (UTC).", example = "2026-01-01")
    @Parameter(name = "createdTo", description = "Created on or before this date (UTC).", example = "2026-12-31")
    @Parameter(name = "stateId", description = "Filter by one or more state ids (Masterdata module) - matches a company with ANY address referencing any of these.")
    @Parameter(name = "cityId", description = "Filter by one or more city ids (Masterdata module) - matches a company with ANY address referencing any of these.")
    @GetMapping
    @PreAuthorize("@authz.has('company.view')")
    public ApiResponse<PageResponse<CompanySummaryResponse>> list(
            @RequestParam(required = false) Set<CompanyStatus> status,
            @RequestParam(required = false) Set<UUID> industryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean gstVerified,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) Set<UUID> stateId,
            @RequestParam(required = false) Set<UUID> cityId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CompanySearchCriteria criteria = new CompanySearchCriteria(status, industryId, search, gstVerified,
                createdFrom, createdTo, stateId, cityId);
        return ApiResponse.ok(mapPage(companyService.list(criteria, pageable), AdminCompanyController::toSummaryResponse));
    }

    @Operation(summary = "Get a company", description = "**Permission:** `company.view`. Full detail "
            + "including the owner reference and every address.")
    @Parameter(name = "id", description = "Company id.")
    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('company.view')")
    public ApiResponse<CompanyResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(companyService.get(id)));
    }

    @Operation(summary = "Create a company", description = """
            **Permission:** `company.create`.

            Creates the company (status `PENDING_VERIFICATION`, GST unverified) and its OWNER login \
            account in one transaction, then enqueues an invite email to the owner (the async outbox \
            path - a transient email-provider hiccup never rolls back the creation). GST/PAN are \
            optional and never verified here - use GST-verify afterward.

            **Validation:** `industryId` must reference an existing industry; `ownerEmail` must not \
            already belong to another company-user account; when given, `gstNumber`/`panNumber` must \
            not already belong to another company; when `addresses` is non-empty, exactly one entry \
            must have `primary=true`.""")
    @PostMapping
    @PreAuthorize("@authz.has('company.create')")
    public ApiResponse<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        return ApiResponse.ok(toResponse(companyService.create(toCommand(request))));
    }

    @Operation(summary = "Update a company's profile", description = "**Permission:** `company.edit`. "
            + "Replaces `name`/`industryId` only - GST/PAN, status, and addresses each have their own "
            + "dedicated endpoint.")
    @Parameter(name = "id", description = "Company id.")
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('company.edit')")
    public ApiResponse<CompanyResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request) {
        return ApiResponse.ok(toResponse(companyService.update(id, new UpdateCompanyCommand(request.name(), request.industryId()))));
    }

    @Operation(summary = "Change a company's approval status", description = """
            **Permission:** `company.manage_status`.

            Explicit target status, never a blind toggle. Allowed: `PENDING_VERIFICATION` &lt;-&gt; \
            `REJECTED`, `PENDING_VERIFICATION` -> `ACTIVE`, `ACTIVE` &lt;-&gt; `SUSPENDED` - every other \
            pair is rejected, including `REJECTED` -> `ACTIVE` directly (must pass back through \
            `PENDING_VERIFICATION`) and `ACTIVE` -> `REJECTED`. `reason` is required for `SUSPENDED`/ \
            `REJECTED`.

            **Side effect:** moving to `SUSPENDED`/`REJECTED` disables every one of this company's login \
            accounts and revokes every one of their refresh tokens, in the same transaction; moving back \
            to `ACTIVE` re-enables them. An already-issued access token is also cut off immediately by \
            the company-user session-policy check once that flow exists - not a gap in this endpoint.""")
    @Parameter(name = "id", description = "Company id.")
    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('company.manage_status')")
    public ApiResponse<CompanyResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyStatusRequest request) {
        return ApiResponse.ok(toResponse(companyService.updateStatus(id, request.status(), request.reason())));
    }

    @Operation(summary = "Bulk change company status", description = """
            **Permission:** `company.manage_status`.

            Applies the exact same guarded transition as Change Company Status (above) to every id \
            independently. Partial success is the normal, expected outcome, not an all-or-nothing \
            failure - one id failing its guard (not found, illegal transition, missing reason, ...) is \
            recorded in `failures[]` and does not roll back the ids that already succeeded.""")
    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('company.manage_status')")
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateCompanyStatusRequest request) {
        return ApiResponse.ok(companyService.bulkUpdateStatus(request.ids(), request.status(), request.reason()));
    }

    @Operation(summary = "List a company's status history", description = "**Permission:** `company.view`. "
            + "Every approval-status transition this company has ever had, newest first - an immutable "
            + "audit trail.")
    @Parameter(name = "id", description = "Company id.")
    @GetMapping("/{id}/status-history")
    @PreAuthorize("@authz.has('company.view')")
    public ApiResponse<PageResponse<CompanyStatusHistoryResponse>> statusHistory(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(mapPage(companyService.listStatusHistory(id, pageable), AdminCompanyController::toHistoryResponse));
    }

    @Operation(summary = "Delete (soft) a company", description = """
            **Permission:** `company.delete`.

            Soft-deletes the company (`deletedAt`, never a real SQL DELETE) and applies the same \
            user-disable-and-revoke-sessions cascade as moving to SUSPENDED/REJECTED. Every satellite row \
            (addresses, status history, the company's own users and their tokens) is left untouched, \
            preserving full history.

            **Precondition:** rejected with a 422 if the company is currently `ACTIVE` - change its \
            status first (e.g. to `SUSPENDED`) via the status endpoint, then delete.""")
    @Parameter(name = "id", description = "Company id.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('company.delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        companyService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "Bulk delete (soft) companies", description = """
            **Permission:** `company.delete`.

            Applies the exact same guarded soft-delete as Delete Company (above) to every id \
            independently. Partial success is expected - an id that's currently `ACTIVE`, or doesn't \
            exist, is recorded in `failures[]` without rolling back the ids that already succeeded.""")
    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('company.delete')")
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(companyService.bulkDelete(request.ids()));
    }

    /** Streams a raw .xlsx file, not the usual {@link ApiResponse} envelope - an exported file is not JSON. */
    @Operation(summary = "Export companies to Excel", description = """
            **Permission:** `company.export`.

            **Returns a raw `.xlsx` file download (`Content-Disposition: attachment`), NOT the usual \
            JSON envelope** - this is the only endpoint on this controller that doesn't return \
            `ApiResponse`. Columns, in order: Company Code, Name, Industry, Status, GST Number, PAN \
            Number, GST Verified (`Yes`/`No`), Created At. GST/PAN are masked unless the caller also \
            holds `company.view_sensitive` - the exact same masking Get Company already applies.

            Give `ids` for an exact set (unknown ids are silently skipped); otherwise every other field \
            is an optional filter with the **exact same semantics as List Companies above** (a \
            non-empty set matches ANY of its values; every non-empty field is ANDed together). Posting \
            `{}` exports every company, unfiltered.""")
    @PostMapping("/export")
    @PreAuthorize("@authz.has('company.export')")
    public ResponseEntity<byte[]> export(@RequestBody CompanyExportRequest request) {
        CompanySearchCriteria criteria = new CompanySearchCriteria(request.status(), request.industryId(), request.search(),
                request.gstVerified(), request.createdFrom(), request.createdTo(), request.stateId(), request.cityId());
        byte[] xlsx = companyService.exportToExcel(request.ids(), criteria);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"companies.xlsx\"")
                .body(xlsx);
    }

    @Operation(summary = "Verify a company's GST number", description = """
            **Permission:** `company.edit`** (reuses Edit - GST verification does not get its own \
            permission code, mirroring how worker bank-account verification reuses an existing code).

            No request body - verifies whatever `gstNumber` is currently on file for this company \
            against the configured GST-verification provider. On success, stamps `gstVerifiedAt`/ \
            `gstVerificationRef`. A provider-reported negative result (invalid pattern, not found, not \
            active) surfaces as a 422 with the provider's own reason and persists nothing, so this can \
            simply be retried once the GST number on file is corrected.""")
    @Parameter(name = "id", description = "Company id.")
    @PostMapping("/{id}/gst-verify")
    @PreAuthorize("@authz.has('company.edit')")
    public ApiResponse<CompanyResponse> verifyGst(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(companyService.triggerGstVerification(id)));
    }

    @Operation(summary = "List a company's addresses", description = "**Permission:** `company.view`.")
    @Parameter(name = "id", description = "Company id.")
    @GetMapping("/{id}/addresses")
    @PreAuthorize("@authz.has('company.view')")
    public ApiResponse<List<CompanyAddressResponse>> listAddresses(@PathVariable UUID id) {
        return ApiResponse.ok(addressService.list(id).stream().map(AdminCompanyController::toAddressResponse).toList());
    }

    @Operation(summary = "Add a company address", description = "**Permission:** `company.edit`. Setting "
            + "`primary=true` demotes whichever address currently holds it - at most one address per "
            + "company may be primary.")
    @Parameter(name = "id", description = "Company id.")
    @PostMapping("/{id}/addresses")
    @PreAuthorize("@authz.has('company.edit')")
    public ApiResponse<CompanyAddressResponse> addAddress(@PathVariable UUID id, @Valid @RequestBody CompanyAddressRequest request) {
        return ApiResponse.ok(toAddressResponse(addressService.add(id, toAddressInput(request))));
    }

    @Operation(summary = "Update a company address", description = "**Permission:** `company.edit`. Full "
            + "replace of the address at `addressId`, except its `addressType` (fixed once created).")
    @Parameter(name = "id", description = "Company id.")
    @Parameter(name = "addressId", description = "Address id - must belong to this company.")
    @PutMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("@authz.has('company.edit')")
    public ApiResponse<CompanyAddressResponse> updateAddress(@PathVariable UUID id, @PathVariable UUID addressId,
            @Valid @RequestBody UpdateCompanyAddressRequest request) {
        UpdateCompanyAddressCommand command = new UpdateCompanyAddressCommand(request.primary(), request.line1(),
                request.line2(), request.stateId(), request.cityId(), request.pincode());
        return ApiResponse.ok(toAddressResponse(addressService.update(id, addressId, command)));
    }

    @Operation(summary = "Delete a company address", description = """
            **Permission:** `company.edit`. Soft-deletes the address (never a real SQL DELETE).

            **Rules:** rejected with a 422 if this is the company's only remaining address - a company \
            must always have at least one. If the deleted address was the primary one and another \
            remains, the next-oldest surviving address is automatically promoted to primary in the \
            same request.""")
    @Parameter(name = "id", description = "Company id.")
    @Parameter(name = "addressId", description = "Address id - must belong to this company.")
    @DeleteMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("@authz.has('company.edit')")
    public ApiResponse<Void> deleteAddress(@PathVariable UUID id, @PathVariable UUID addressId) {
        addressService.delete(id, addressId);
        return ApiResponse.ok();
    }

    private static CreateCompanyCommand toCommand(CreateCompanyRequest request) {
        List<CompanyAddressInput> addressInputs = request.addresses().stream()
                .map(AdminCompanyController::toAddressInput).toList();
        return new CreateCompanyCommand(request.name(), request.industryId(), request.gstNumber(), request.panNumber(),
                addressInputs, request.ownerName(), request.ownerEmail(), request.ownerPhone());
    }

    private static CompanyAddressInput toAddressInput(CompanyAddressRequest request) {
        return new CompanyAddressInput(request.addressType(), request.primary(), request.line1(), request.line2(),
                request.stateId(), request.cityId(), request.pincode());
    }

    private static CompanyResponse toResponse(CompanyView view) {
        CompanyOwnerView owner = view.owner();
        CompanyOwnerRef ownerRef = owner == null ? null : new CompanyOwnerRef(owner.id(), owner.name(), owner.email());
        List<CompanyAddressResponse> addressResponses = view.addresses().stream()
                .map(AdminCompanyController::toAddressResponse).toList();
        return new CompanyResponse(view.id(), view.companyCode(), view.name(), view.industryId(), view.gstNumber(),
                view.panNumber(), view.gstVerifiedAt(), view.gstVerificationRef(), view.status(), view.statusReason(),
                ownerRef, addressResponses, view.createdAt());
    }

    private static CompanySummaryResponse toSummaryResponse(CompanySummaryView view) {
        return new CompanySummaryResponse(view.id(), view.companyCode(), view.name(), view.industryId(),
                view.status(), view.createdAt());
    }

    private static CompanyAddressResponse toAddressResponse(CompanyAddressView view) {
        return new CompanyAddressResponse(view.id(), view.addressType(), view.primary(), view.line1(), view.line2(),
                view.stateId(), view.cityId(), view.pincode());
    }

    private static CompanyStatusHistoryResponse toHistoryResponse(CompanyStatusHistoryView view) {
        return new CompanyStatusHistoryResponse(view.id(), view.fromStatus(), view.toStatus(), view.reason(),
                view.changedByAdminEmail(), view.changedAt());
    }

    /** No {@code PageResponse.map(...)} exists on purpose (it is a plain transport record, not a functor) - this reconstructs one with its content mapped, keeping every paging field intact. */
    private static <T, R> PageResponse<R> mapPage(PageResponse<T> page, Function<T, R> mapper) {
        return new PageResponse<>(page.content().stream().map(mapper).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.first(), page.last());
    }
}
