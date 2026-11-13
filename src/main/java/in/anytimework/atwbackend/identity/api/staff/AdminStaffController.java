package in.anytimework.atwbackend.identity.api.staff;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserAddressResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserBankDetailResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserKycResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserProfileResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAddressRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateBankDetailRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateKycRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateProfileRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffStatusRequest;
import in.anytimework.atwbackend.identity.application.AdminStaffAddressService;
import in.anytimework.atwbackend.identity.application.AdminStaffBankDetailService;
import in.anytimework.atwbackend.identity.application.AdminStaffKycService;
import in.anytimework.atwbackend.identity.application.AdminStaffProfileService;
import in.anytimework.atwbackend.identity.application.AdminStaffService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Staff/admin account CRUD, role assignment, and the profile/KYC/bank-detail sub-resources.
 * Every endpoint requires the matching {@code admin_staff.*} permission; KYC and bank-detail reads are
 * additionally masked unless the caller also holds {@code admin_staff.view_kyc} /
 * {@code admin_staff.view_bank_details} (see AdminStaffKycService / AdminStaffBankDetailService).
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/staff")
@Tag(name = "Admin Staff",
        description = "Staff/admin account CRUD, role assignment, and the profile/address/KYC/bank-detail "
                + "sub-resources, under `/api/v1/admin/staff/**`. This is the admin side only - ATW's own "
                + "internal platform staff, not workers or companies. Requires `Authorization: Bearer` and the "
                + "matching `admin_staff.*` permission (7 codes: `view`/`create`/`edit`/`delete`/`toggle_status`/"
                + "`view_kyc`/`view_bank_details`).\n\n"
                + "**Privilege-escalation guard:** the same rule as Admin Roles applies whenever this module "
                + "assigns roles to a staff member (Create/Update Staff) - a non-super-admin can never hand out a "
                + "role carrying a permission they don't hold themselves.\n\n"
                + "**Masking:** KYC values and the bank account number are returned masked (all but the last 4 "
                + "characters replaced with `*`) to a caller holding only `admin_staff.view`; seeing them unmasked "
                + "additionally requires `admin_staff.view_kyc` / `admin_staff.view_bank_details` - by default only "
                + "the Senior Developer and Junior Developer seed roles hold those two. Both values are also "
                + "encrypted at rest (AES-256-GCM) independently of masking - masking controls what a given API "
                + "caller sees, encryption protects the data if the database itself is ever compromised.\n\n"
                + "**Address is plain text, not encrypted** (a deliberate 2026-11-13 change, confirmed with the "
                + "developer) - lower-sensitivity than an Aadhaar/PAN/bank-account number, so it does not use the "
                + "same encryption mechanism as KYC/bank-detail.\n\n"
                + "Profile/address/KYC/bank-detail rows are not created by Create Staff - they start fully empty "
                + "(every field `null`, no 404) and are populated later via their own PUT endpoints, independently "
                + "and progressively.")
public class AdminStaffController {

    private final AdminStaffService staffService;
    private final AdminStaffProfileService profileService;
    private final AdminStaffAddressService addressService;
    private final AdminStaffKycService kycService;
    private final AdminStaffBankDetailService bankDetailService;

    public AdminStaffController(AdminStaffService staffService, AdminStaffProfileService profileService,
                           AdminStaffAddressService addressService, AdminStaffKycService kycService,
                           AdminStaffBankDetailService bankDetailService) {
        this.staffService = staffService;
        this.profileService = profileService;
        this.addressService = addressService;
        this.kycService = kycService;
        this.bankDetailService = bankDetailService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "List staff/admin accounts",
            description = "Standard Spring pagination via query params: `page` (0-based, default `0`), `size` "
                    + "(default `100`, capped at `500` - set application-wide by "
                    + "`spring.data.web.pageable.default-page-size`/`max-page-size` in `application.properties`, "
                    + "not something this endpoint overrides), `sort` (e.g. `sort=fullName,asc`). Returns a "
                    + "`PageResponse<AdminStaffResponse>`. KYC/bank fields are **not** included here - fetch them "
                    + "per-staff-member via the sub-resource endpoints below.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "A page of staff accounts.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "content": [
                                  {
                                    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                    "username": null,
                                    "email": "new.staff@example.com",
                                    "fullName": "New Staff Member",
                                    "phone": null,
                                    "superAdmin": false,
                                    "status": "ACTIVE",
                                    "roles": [{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "QA Tester" }],
                                    "lastLoginAt": null,
                                    "createdAt": "2026-10-24T10:23:00Z"
                                  }
                                ],
                                "page": 0,
                                "size": 100,
                                "totalElements": 1,
                                "totalPages": 1,
                                "first": true,
                                "last": true
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks `admin_staff.view`.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "ACCESS_DENIED",
                                "message": "You do not have permission to perform this action",
                                "fieldErrors": null,
                                "timestamp": "2026-10-24T10:22:00Z",
                                "traceId": "c3d4e5f607182930"
                              }
                            }""")))
    })
    public ApiResponse<PageResponse<AdminStaffResponse>> list(Pageable pageable) {
        return ApiResponse.ok(staffService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "Get a staff/admin account by id",
            description = "Returns the core `AdminStaffResponse` record only - profile/address/KYC/bank-detail "
                    + "live in the sub-resource endpoints below.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Staff member found.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "username": null,
                                "email": "new.staff@example.com",
                                "fullName": "New Staff Member",
                                "phone": null,
                                "superAdmin": false,
                                "status": "ACTIVE",
                                "roles": [{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "QA Tester" }],
                                "lastLoginAt": null,
                                "createdAt": "2026-10-24T10:23:00Z"
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No staff member with this id.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:30:00Z",
                                "traceId": "f6071829303a4b5c"
                              }
                            }""")))
    })
    public ApiResponse<AdminStaffResponse> get(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ApiResponse.ok(staffService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('admin_staff.create')")
    @Operation(summary = "Create a staff/admin account",
            description = "Checks run in this order: (1) `email` must not already belong to another admin "
                    + "(case-insensitive) - `409 CONFLICT`; (2) if `username` is provided, it must not already be "
                    + "taken (case-insensitive) - `409 CONFLICT`; (3) every `roleIds` entry must exist - "
                    + "`400 VALIDATION_ERROR`; (4) unless the caller is a super-admin, the union of the requested "
                    + "roles' permissions must be entirely within the caller's own current permissions - "
                    + "`422 BUSINESS_RULE_VIOLATION`. On success: audits `ADMIN_STAFF_CREATED` and sends an invite "
                    + "email (template `ADMIN_STAFF_INVITE`) with a set-password link - the recipient completes "
                    + "onboarding via Auth > Accept Invite. Profile/address/KYC/bank-detail rows are not created "
                    + "here - they start out empty (all-null) and are set separately via the sub-resource endpoints.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Staff account created.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "username": null,
                                "email": "new.staff@example.com",
                                "fullName": "New Staff Member",
                                "phone": null,
                                "superAdmin": false,
                                "status": "ACTIVE",
                                "roles": [{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "QA Tester" }],
                                "lastLoginAt": null,
                                "createdAt": "2026-10-24T10:23:00Z"
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already belongs to another admin.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "CONFLICT",
                                "message": "An admin with this email already exists.",
                                "fieldErrors": null,
                                "timestamp": "2026-10-24T10:23:30Z",
                                "traceId": "d4e5f6071829303a"
                              }
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Caller tried to assign a role carrying a permission they don't hold themselves.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "You cannot grant a permission you do not hold yourself.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:31:00Z",
                                "traceId": "071829303a4b5c6d"
                              }
                            }""")))
    })
    public ApiResponse<AdminStaffResponse> create(@Valid @RequestBody CreateAdminStaffRequest request, HttpServletRequest http) {
        return ApiResponse.ok(staffService.create(request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    @Operation(summary = "Update a staff/admin account",
            description = "Full replace of `username`/`fullName`/`phone`/roles. `email` is intentionally not "
                    + "editable here. Checks run in this order: (1) staff id must exist - `404`; (2) target must "
                    + "not be a super-admin account - `422`; (3) if `username` changed, it must not already be "
                    + "taken by someone else - `409`; (4) unknown role id - `400`; (5) privilege-escalation, same "
                    + "rule as Create Staff - `422`.\n\n**Side effect - forced re-login, only when roles actually "
                    + "changed:** if the new role set differs from before, the staff member's `security_version` is "
                    + "bumped and every refresh token they hold is revoked, so a permission *removal* takes effect "
                    + "immediately instead of riding out the access token's remaining TTL (up to 15 min). A plain "
                    + "name/phone edit with the same roles does **not** trigger this.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Staff account updated.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "username": null,
                                "email": "new.staff@example.com",
                                "fullName": "Updated Name",
                                "phone": null,
                                "superAdmin": false,
                                "status": "ACTIVE",
                                "roles": [{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "QA Tester" }],
                                "lastLoginAt": null,
                                "createdAt": "2026-10-24T10:23:00Z"
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Target is a super-admin account.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "Super-admin accounts cannot be modified through this endpoint.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:32:00Z",
                                "traceId": "1829303a4b5c6d7e"
                              }
                            }""")))
    })
    public ApiResponse<AdminStaffResponse> update(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminStaffRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(staffService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** Named "status", not "toggle": an explicit target status avoids a race between two quick toggle calls. */
    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('admin_staff.toggle_status')")
    @Operation(summary = "Activate or disable a staff/admin account",
            description = "Takes an explicit target `status` rather than blindly toggling, so two quick successive "
                    + "calls can't race into an unintended end state. Rejected with `422 BUSINESS_RULE_VIOLATION` "
                    + "if the target is your own account, or a super-admin account (message substitutes "
                    + "\"activated\"/\"deactivated\" depending on the requested status).\n\n**Side effect:** setting "
                    + "`DISABLED` immediately revokes every refresh token for that admin (no new access token can be "
                    + "minted), **and** any access token they're still mid-use with is rejected on its very next "
                    + "request - `AdminSessionPolicyFilter` checks `active` status on every authenticated call, not "
                    + "just at token-issue time; there is no lag waiting for the current token to expire. Audits "
                    + "`ADMIN_STAFF_STATUS_CHANGED` with the before/after transition.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                "username": null,
                                "email": "new.staff@example.com",
                                "fullName": "New Staff Member",
                                "phone": null,
                                "superAdmin": false,
                                "status": "DISABLED",
                                "roles": [{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "QA Tester" }],
                                "lastLoginAt": null,
                                "createdAt": "2026-10-24T10:23:00Z"
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Cannot disable your own account.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "You cannot deactivate your own account.",
                                "fieldErrors": null,
                                "timestamp": "2026-10-24T10:24:15Z",
                                "traceId": "e5f6071829303a4b"
                              }
                            }""")))
    })
    public ApiResponse<AdminStaffResponse> updateStatus(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminStaffStatusRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(staffService.updateStatus(id, request.status(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.delete')")
    @Operation(summary = "Delete a staff/admin account",
            description = "**Soft-deletes** the account: sets `deletedAt` (`AdminUser.markDeleted`) rather than "
                    + "ever issuing a real SQL DELETE (`AdminUser`'s `@SQLRestriction(\"deleted_at is null\")` then "
                    + "makes this admin invisible to every existing query path - `findById`, login lookups, the "
                    + "pessimistic-lock JPQL - automatically). This corrects this endpoint's previous documentation, "
                    + "which described a hard delete; as of the `V29__admin_user_soft_delete.sql` migration it is "
                    + "not one.\n\n"
                    + "Every one of this admin's own satellite rows (profile, address, KYC - including PF/ESIC "
                    + "numbers that may be needed for post-departure statutory reporting - bank detail, and role "
                    + "assignments) is left completely untouched, preserving full history for audit/reconciliation. "
                    + "The deleted admin's username and email both become available again for a new hire to be "
                    + "created with (a partial unique index scoped to `deleted_at IS NULL`, the same reuse-after-"
                    + "delete decision already made for `Worker`'s mobile number). Past audit log entries referencing "
                    + "this admin are unaffected: `admin_audit_log.actor_admin_id` becomes `NULL` "
                    + "(`ON DELETE SET NULL` - inert against a soft delete today, kept for the historical hard-delete "
                    + "FK), but `actor_email` is a point-in-time snapshot captured when each log entry was "
                    + "originally written, so \"who did this\" stays readable even after the actor is deleted. "
                    + "There is deliberately no restore/undelete endpoint yet - nothing is destroyed, so one can be "
                    + "added later purely at the application layer, with no migration changes.\n\n"
                    + "Rejected with `422 BUSINESS_RULE_VIOLATION` if the target is a super-admin account or your "
                    + "own account (same guard as Set Staff Status).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Staff account soft-deleted.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Cannot delete your own account.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "You cannot delete your own account.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:33:00Z",
                                "traceId": "29303a4b5c6d7e8f"
                              }
                            }""")))
    })
    public ApiResponse<Void> delete(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            HttpServletRequest http) {
        staffService.delete(id, HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "Get a staff member's demographic profile",
            description = "Returns `{dateOfBirth, gender}` - every field `null` until explicitly set via Update "
                    + "Staff Profile (no profile row is created automatically by Create Staff; this returns an "
                    + "all-null object rather than a 404 in that case). Not sensitive data; never masked, no extra "
                    + "permission needed beyond `admin_staff.view`. Address used to live on this DTO - it is now "
                    + "its own sub-resource (`GET/PUT .../address`) with real `stateId`/`cityId` foreign keys "
                    + "instead of a free-text line.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile (possibly all-null).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": { "dateOfBirth": "1995-06-15", "gender": "FEMALE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Staff id doesn't exist.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:34:00Z",
                                "traceId": "303a4b5c6d7e8f90"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserProfileResponse> getProfile(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ApiResponse.ok(profileService.get(id));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    @Operation(summary = "Replace a staff member's demographic profile",
            description = "Every field optional (captured progressively, not necessarily all at once) - send "
                    + "`null` for any field you don't want to set yet. Full replace of both fields (not a merge): "
                    + "omitting a field that was previously set clears it to `null`. Creates the profile row on "
                    + "first write if it doesn't exist yet. Rejected with `422 BUSINESS_RULE_VIOLATION` if the "
                    + "target is a super-admin account. Audits `ADMIN_STAFF_PROFILE_UPDATED`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile replaced.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": { "dateOfBirth": "1995-06-15", "gender": "FEMALE" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Staff id doesn't exist.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:34:30Z",
                                "traceId": "3a4b5c6d7e8f9001"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserProfileResponse> updateProfile(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(profileService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @GetMapping("/{id}/address")
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "Get a staff member's address",
            description = "Returns `{line1, line2, stateId, cityId, pincode, latitude, longitude, formattedAddress}`. "
                    + "`stateId`/`cityId` reference the Masterdata > Geography lookup tables rather than a free-text "
                    + "state/city name. `latitude`/`longitude`/`formattedAddress` are groundwork for a future "
                    + "geocoding integration - always `null` today, not yet settable through this API.\n\n"
                    + "If no address has ever been saved for this staff member, this returns an all-`null` object, "
                    + "**not** a 404 - mirrors Get Staff Profile's \"every field null until set\" behavior. "
                    + "`line1`/`line2` are stored as **plain text, not encrypted** (a deliberate 2026-11-13 change) "
                    + "- unlike KYC/bank-detail below, which stay encrypted; an address is treated as lower-"
                    + "sensitivity than an Aadhaar/PAN/bank-account number.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Address (possibly all-null).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "line1": "221B Test Street",
                                "line2": null,
                                "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2",
                                "cityId": "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01",
                                "pincode": "302001",
                                "latitude": null,
                                "longitude": null,
                                "formattedAddress": null
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Staff id doesn't exist.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:35:00Z",
                                "traceId": "4b5c6d7e8f900112"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserAddressResponse> getAddress(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ApiResponse.ok(addressService.get(id));
    }

    @PutMapping("/{id}/address")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    @Operation(summary = "Replace a staff member's address",
            description = "Every field optional (progressive capture, same philosophy as Update Staff Profile). "
                    + "Full replace (not a merge) - creates the address row on first write. Stored as plain text, "
                    + "not encrypted (see Get Staff Address). Checks, in order: (1) staff id must exist - `404`; "
                    + "(2) target must not be a super-admin account - `422`; (3) `stateId`, if provided, must "
                    + "reference an existing Masterdata > Geography row - `400` (\"Unknown state.\"); (4) `cityId`, "
                    + "if provided, likewise - `400` (\"Unknown city.\"). Audits `ADMIN_STAFF_ADDRESS_UPDATED`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Address replaced.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "line1": "221B Test Street",
                                "line2": null,
                                "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2",
                                "cityId": "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01",
                                "pincode": "302001",
                                "latitude": null,
                                "longitude": null,
                                "formattedAddress": null
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unknown stateId/cityId.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "VALIDATION_ERROR",
                                "message": "Unknown state.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-01T10:25:00Z",
                                "traceId": "29303a4b5c6d7e8f"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserAddressResponse> updateAddress(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAddressRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(addressService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** admin_staff.view alone returns masked values; admin_staff.view_kyc (Senior/Junior Developer only) sees them unmasked. */
    @GetMapping("/{id}/kyc")
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "Get a staff member's KYC numbers",
            description = "Returns `{aadhaarNumber, panNumber, uanNumber, pfNumber, esicNumber}` - all `null` "
                    + "until set (no 404 if never saved). Values are encrypted at rest (AES-256-GCM, random IV per "
                    + "value) - transparent to this API, decrypted on read.\n\n**Masking:** `admin_staff.view` alone "
                    + "(checked at the controller for both GET and PUT) only ever returns **masked** values - all "
                    + "but the last 4 characters replaced with `*` (e.g. `\"********9012\"`), or fully masked if 4 "
                    + "characters or shorter. Seeing the **unmasked** value additionally requires "
                    + "`admin_staff.view_kyc`, which by default only the Senior Developer and Junior Developer seed "
                    + "roles hold. Masking is applied purely in the response - the stored value is unaffected either way.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Masked (caller holds only `admin_staff.view`).",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Masked (admin_staff.view only)", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "aadhaarNumber": "********9012",
                                        "panNumber": "******34F",
                                        "uanNumber": null,
                                        "pfNumber": null,
                                        "esicNumber": null
                                      },
                                      "error": null
                                    }"""),
                            @ExampleObject(name = "Unmasked (admin_staff.view_kyc)", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "aadhaarNumber": "123456789012",
                                        "panNumber": "ABCDE1234F",
                                        "uanNumber": null,
                                        "pfNumber": null,
                                        "esicNumber": null
                                      },
                                      "error": null
                                    }""")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Staff id doesn't exist.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:36:00Z",
                                "traceId": "5c6d7e8f90011223"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserKycResponse> getKyc(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ApiResponse.ok(kycService.get(id));
    }

    @PutMapping("/{id}/kyc")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    @Operation(summary = "Replace a staff member's KYC numbers",
            description = "Every field optional (progressive capture): `aadhaarNumber`/`panNumber`/`uanNumber` max "
                    + "20 chars, `pfNumber` max 50, `esicNumber` max 30. Deliberately no format/checksum validation "
                    + "(e.g. Aadhaar's 12-digit pattern, PAN's checksum letter) - only a length cap, to avoid "
                    + "rejecting legitimate input against a guessed-at pattern that might not match real-world data "
                    + "exactly. Full replace of all 5 fields; creates the KYC row on first write if it doesn't exist "
                    + "yet. The write side has no separate \"can write KYC\" permission - `admin_staff.edit` alone "
                    + "is sufficient, deliberately, to avoid an unused extra knob; only *reading* unmasked is gated "
                    + "further. The response is masked using the exact same rule as Get Staff KYC - note that even "
                    + "the admin who just *wrote* a value gets it back masked if they only hold `admin_staff.view`, "
                    + "not `admin_staff.edit`+`admin_staff.view_kyc` together. Rejected with "
                    + "`422 BUSINESS_RULE_VIOLATION` if the target is a super-admin account. Audits "
                    + "`ADMIN_STAFF_KYC_UPDATED`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "KYC replaced (masked per the caller's permissions).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "aadhaarNumber": "********9012",
                                "panNumber": "******34F",
                                "uanNumber": null,
                                "pfNumber": null,
                                "esicNumber": null
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Target is a super-admin account.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "Super-admin accounts cannot be modified through this endpoint.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:36:30Z",
                                "traceId": "6d7e8f9001122334"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserKycResponse> updateKyc(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKycRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(kycService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** admin_staff.view alone returns a masked account number; admin_staff.view_bank_details sees it unmasked. */
    @GetMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('admin_staff.view')")
    @Operation(summary = "Get a staff member's bank/payout details",
            description = "Returns `{accountHolderName, bankName, accountNumber, ifscCode, branchName}` - all "
                    + "`null` until set (no 404 if never saved). `accountNumber` is encrypted at rest (same "
                    + "mechanism as KYC).\n\n**Masking:** `accountNumber` follows the exact same masking rule as "
                    + "KYC above, gated by `admin_staff.view_bank_details` instead of `admin_staff.view_kyc` (same "
                    + "two seed roles hold it by default, but they are two independent permission codes - holding "
                    + "one does not imply the other). `accountHolderName`, `bankName`, `ifscCode`, `branchName` are "
                    + "**never masked** - only the account number is treated as sensitive here.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Masked (caller holds only `admin_staff.view`).",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Masked (admin_staff.view only)", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accountHolderName": "Staff Holder",
                                        "bankName": "Test Bank",
                                        "accountNumber": "********2333",
                                        "ifscCode": "TEST0001234",
                                        "branchName": "MG Road"
                                      },
                                      "error": null
                                    }"""),
                            @ExampleObject(name = "Unmasked (admin_staff.view_bank_details)", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accountHolderName": "Staff Holder",
                                        "bankName": "Test Bank",
                                        "accountNumber": "000111222333",
                                        "ifscCode": "TEST0001234",
                                        "branchName": "MG Road"
                                      },
                                      "error": null
                                    }""")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Staff id doesn't exist.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "RESOURCE_NOT_FOUND",
                                "message": "Staff member not found",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:37:00Z",
                                "traceId": "7e8f900112233445"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserBankDetailResponse> getBankDetail(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ApiResponse.ok(bankDetailService.get(id));
    }

    @PutMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    @Operation(summary = "Replace a staff member's bank/payout details",
            description = "Every field optional (progressive capture): `accountHolderName`/`bankName`/`branchName` "
                    + "max 150 chars, `accountNumber` max 30, `ifscCode` max 15. No IFSC-format or account-number-"
                    + "checksum validation, same rationale as KYC. Full replace of all 5 fields; creates the row on "
                    + "first write if it doesn't exist yet. Same no-separate-write-permission reasoning as Update "
                    + "Staff KYC. The response's `accountNumber` is masked using the same rule as Get Staff Bank "
                    + "Detail. Rejected with `422 BUSINESS_RULE_VIOLATION` if the target is a super-admin account. "
                    + "Audits `ADMIN_STAFF_BANK_DETAIL_UPDATED`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bank detail replaced (masked per the caller's permissions).",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "data": {
                                "accountHolderName": "Staff Holder",
                                "bankName": "Test Bank",
                                "accountNumber": "********2333",
                                "ifscCode": "TEST0001234",
                                "branchName": "MG Road"
                              },
                              "error": null
                            }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Target is a super-admin account.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "data": null,
                              "error": {
                                "code": "BUSINESS_RULE_VIOLATION",
                                "message": "Super-admin accounts cannot be modified through this endpoint.",
                                "fieldErrors": null,
                                "timestamp": "2026-11-13T10:37:30Z",
                                "traceId": "8f90011223344556"
                              }
                            }""")))
    })
    public ApiResponse<AdminUserBankDetailResponse> updateBankDetail(
            @Parameter(description = "Staff/admin account id (UUID).", required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBankDetailRequest request,
            HttpServletRequest http) {
        return ApiResponse.ok(bankDetailService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }
}
