package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserBankDetailDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateBankDetailRequest;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserBankDetail;
import in.anytimework.atwbackend.identity.domain.AdminUserBankDetailRepository;
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
 * Bank/payout details for a staff member; the account number is encrypted at rest.
 * {@code admin_staff.view} (checked at the controller) only ever sees a masked account number from here;
 * the full decrypted value is returned only to a caller who also holds
 * {@code admin_staff.view_bank_details}.
 */
@Service
public class AdminStaffBankDetailService {

    private final AdminUserRepository adminUsers;
    private final AdminUserBankDetailRepository bankDetails;
    private final AdminAuditService audit;
    private final CurrentAdmin currentAdmin;
    private final PermissionChecker permissionChecker;

    public AdminStaffBankDetailService(AdminUserRepository adminUsers, AdminUserBankDetailRepository bankDetails,
                                  AdminAuditService audit, CurrentAdmin currentAdmin,
                                  PermissionChecker permissionChecker) {
        this.adminUsers = adminUsers;
        this.bankDetails = bankDetails;
        this.audit = audit;
        this.currentAdmin = currentAdmin;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public AdminUserBankDetailDto get(UUID staffId) {
        requireStaffExists(staffId);
        return bankDetails.findByAdminUserId(staffId).map(this::toResponseDto)
                .orElse(new AdminUserBankDetailDto(null, null, null, null, null));
    }

    @Transactional
    public AdminUserBankDetailDto update(UUID staffId, UpdateBankDetailRequest request, String ip, String userAgent) {
        AdminUser admin = requireStaffExists(staffId);
        if (admin.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be modified through this endpoint.");
        }
        AdminUserBankDetail detail = bankDetails.findByAdminUserId(staffId)
                .orElseGet(() -> new AdminUserBankDetail(staffId));
        detail.update(request.accountHolderName(), request.bankName(), request.accountNumber(),
                request.ifscCode(), request.branchName());
        detail = bankDetails.save(detail);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_BANK_DETAIL_UPDATED", staffId.toString(), ip, userAgent);
        return toResponseDto(detail);
    }

    private AdminUser requireStaffExists(UUID staffId) {
        return adminUsers.findById(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    private AdminUserBankDetailDto toResponseDto(AdminUserBankDetail detail) {
        boolean unmasked = permissionChecker.has("admin_staff.view_bank_details");
        String accountNumber = unmasked ? detail.getAccountNumber() : PiiMasking.maskTail(detail.getAccountNumber());
        return new AdminUserBankDetailDto(detail.getAccountHolderName(), detail.getBankName(),
                accountNumber, detail.getIfscCode(), detail.getBranchName());
    }
}
