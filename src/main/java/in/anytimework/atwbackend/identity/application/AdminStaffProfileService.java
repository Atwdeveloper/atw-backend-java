package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserProfileDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateProfileRequest;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserProfile;
import in.anytimework.atwbackend.identity.domain.AdminUserProfileRepository;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Ordinary demographic profile data for a staff member - no masking needed, unlike KYC/bank detail. */
@Service
public class AdminStaffProfileService {

    private final AdminUserRepository adminUsers;
    private final AdminUserProfileRepository profiles;
    private final AdminAuditService audit;
    private final CurrentAdmin currentAdmin;

    public AdminStaffProfileService(AdminUserRepository adminUsers, AdminUserProfileRepository profiles,
                               AdminAuditService audit, CurrentAdmin currentAdmin) {
        this.adminUsers = adminUsers;
        this.profiles = profiles;
        this.audit = audit;
        this.currentAdmin = currentAdmin;
    }

    @Transactional(readOnly = true)
    public AdminUserProfileDto get(UUID staffId) {
        requireStaffExists(staffId);
        return profiles.findByAdminUserId(staffId).map(this::toDto)
                .orElse(new AdminUserProfileDto(null, null, null, null));
    }

    @Transactional
    public AdminUserProfileDto update(UUID staffId, UpdateProfileRequest request, String ip, String userAgent) {
        AdminUser admin = requireStaffExists(staffId);
        if (admin.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be modified through this endpoint.");
        }
        AdminUserProfile profile = profiles.findByAdminUserId(staffId).orElseGet(() -> new AdminUserProfile(staffId));
        profile.update(request.dateOfBirth(), request.gender(), request.addressLine(), request.pincode());
        profile = profiles.save(profile);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_PROFILE_UPDATED", staffId.toString(), ip, userAgent);
        return toDto(profile);
    }

    private AdminUser requireStaffExists(UUID staffId) {
        return adminUsers.findById(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    private AdminUserProfileDto toDto(AdminUserProfile profile) {
        return new AdminUserProfileDto(profile.getDateOfBirth(), profile.getGender(),
                profile.getAddressLine(), profile.getPincode());
    }
}
