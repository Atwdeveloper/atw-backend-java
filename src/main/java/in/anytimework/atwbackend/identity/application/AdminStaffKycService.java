package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserKycDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateKycRequest;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserKyc;
import in.anytimework.atwbackend.identity.domain.AdminUserKycRepository;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Government-ID KYC numbers for a staff member, encrypted at rest. {@code admin_staff.view} (checked at
 * the controller) only ever sees masked values from here; the full decrypted value is returned
 * only to a caller who also holds {@code admin_staff.view_kyc}.
 */
@Service
public class AdminStaffKycService {

    private final AdminUserRepository adminUsers;
    private final AdminUserKycRepository kycs;
    private final AdminAuditService audit;
    private final CurrentAdmin currentAdmin;
    private final PermissionChecker permissionChecker;

    public AdminStaffKycService(AdminUserRepository adminUsers, AdminUserKycRepository kycs, AdminAuditService audit,
                           CurrentAdmin currentAdmin, PermissionChecker permissionChecker) {
        this.adminUsers = adminUsers;
        this.kycs = kycs;
        this.audit = audit;
        this.currentAdmin = currentAdmin;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public AdminUserKycDto get(UUID staffId) {
        requireStaffExists(staffId);
        return kycs.findByAdminUserId(staffId).map(this::toResponseDto)
                .orElse(new AdminUserKycDto(null, null, null, null, null));
    }

    @Transactional
    public AdminUserKycDto update(UUID staffId, UpdateKycRequest request, String ip, String userAgent) {
        AdminUser admin = requireStaffExists(staffId);
        if (admin.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be modified through this endpoint.");
        }
        AdminUserKyc kyc = kycs.findByAdminUserId(staffId).orElseGet(() -> new AdminUserKyc(staffId));
        kyc.update(request.aadhaarNumber(), request.panNumber(), request.uanNumber(),
                request.pfNumber(), request.esicNumber());
        kyc = kycs.save(kyc);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_KYC_UPDATED", staffId.toString(), ip, userAgent);
        return toResponseDto(kyc);
    }

    private AdminUser requireStaffExists(UUID staffId) {
        return adminUsers.findById(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    private AdminUserKycDto toResponseDto(AdminUserKyc kyc) {
        boolean unmasked = permissionChecker.has("admin_staff.view_kyc");
        return new AdminUserKycDto(
                mask(kyc.getAadhaarNumber(), unmasked),
                mask(kyc.getPanNumber(), unmasked),
                mask(kyc.getUanNumber(), unmasked),
                mask(kyc.getPfNumber(), unmasked),
                mask(kyc.getEsicNumber(), unmasked));
    }

    private static String mask(String value, boolean unmasked) {
        return unmasked ? value : PiiMasking.maskTail(value);
    }
}
