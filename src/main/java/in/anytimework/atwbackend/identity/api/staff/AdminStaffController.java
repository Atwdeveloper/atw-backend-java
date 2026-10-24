package in.anytimework.atwbackend.identity.api.staff;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserBankDetailDto;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserKycDto;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserProfileDto;
import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateBankDetailRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateKycRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateProfileRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffStatusRequest;
import in.anytimework.atwbackend.identity.application.AdminStaffBankDetailService;
import in.anytimework.atwbackend.identity.application.AdminStaffKycService;
import in.anytimework.atwbackend.identity.application.AdminStaffProfileService;
import in.anytimework.atwbackend.identity.application.AdminStaffService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.security.HttpRequests;
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
public class AdminStaffController {

    private final AdminStaffService staffService;
    private final AdminStaffProfileService profileService;
    private final AdminStaffKycService kycService;
    private final AdminStaffBankDetailService bankDetailService;

    public AdminStaffController(AdminStaffService staffService, AdminStaffProfileService profileService,
                           AdminStaffKycService kycService, AdminStaffBankDetailService bankDetailService) {
        this.staffService = staffService;
        this.profileService = profileService;
        this.kycService = kycService;
        this.bankDetailService = bankDetailService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('admin_staff.view')")
    public ApiResponse<PageResponse<AdminStaffDto>> list(Pageable pageable) {
        return ApiResponse.ok(staffService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.view')")
    public ApiResponse<AdminStaffDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('admin_staff.create')")
    public ApiResponse<AdminStaffDto> create(@Valid @RequestBody CreateAdminStaffRequest request, HttpServletRequest http) {
        return ApiResponse.ok(staffService.create(request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    public ApiResponse<AdminStaffDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminStaffRequest request,
                                        HttpServletRequest http) {
        return ApiResponse.ok(staffService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** Named "status", not "toggle": an explicit target status avoids a race between two quick toggle calls. */
    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('admin_staff.toggle_status')")
    public ApiResponse<AdminStaffDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAdminStaffStatusRequest request,
                                              HttpServletRequest http) {
        return ApiResponse.ok(staffService.updateStatus(id, request.status(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('admin_staff.delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        staffService.delete(id, HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("@authz.has('admin_staff.view')")
    public ApiResponse<AdminUserProfileDto> getProfile(@PathVariable UUID id) {
        return ApiResponse.ok(profileService.get(id));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    public ApiResponse<AdminUserProfileDto> updateProfile(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request,
                                                           HttpServletRequest http) {
        return ApiResponse.ok(profileService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** admin_staff.view alone returns masked values; admin_staff.view_kyc (Senior/Junior Developer only) sees them unmasked. */
    @GetMapping("/{id}/kyc")
    @PreAuthorize("@authz.has('admin_staff.view')")
    public ApiResponse<AdminUserKycDto> getKyc(@PathVariable UUID id) {
        return ApiResponse.ok(kycService.get(id));
    }

    @PutMapping("/{id}/kyc")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    public ApiResponse<AdminUserKycDto> updateKyc(@PathVariable UUID id, @Valid @RequestBody UpdateKycRequest request,
                                                  HttpServletRequest http) {
        return ApiResponse.ok(kycService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }

    /** admin_staff.view alone returns a masked account number; admin_staff.view_bank_details sees it unmasked. */
    @GetMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('admin_staff.view')")
    public ApiResponse<AdminUserBankDetailDto> getBankDetail(@PathVariable UUID id) {
        return ApiResponse.ok(bankDetailService.get(id));
    }

    @PutMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('admin_staff.edit')")
    public ApiResponse<AdminUserBankDetailDto> updateBankDetail(@PathVariable UUID id,
                                                                 @Valid @RequestBody UpdateBankDetailRequest request,
                                                                 HttpServletRequest http) {
        return ApiResponse.ok(bankDetailService.update(id, request, HttpRequests.clientIp(http), HttpRequests.userAgent(http)));
    }
}
