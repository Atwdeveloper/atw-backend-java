package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.application.dto.AdminProfile;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authenticated self-service for the current admin: profile, effective permissions, password change. */
@Service
public class AdminAccountService {

    private final AdminUserRepository adminUsers;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;
    private final AdminAuditService audit;

    public AdminAccountService(AdminUserRepository adminUsers, PasswordEncoder passwordEncoder,
                               RefreshTokenService refreshTokenService, NotificationService notificationService,
                               AdminAuditService audit) {
        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.notificationService = notificationService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminProfile profile(UUID adminId) {
        AdminUser admin = adminUsers.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        List<String> authorities = admin.isSuperAdmin()
                ? List.of("SUPER_ADMIN")
                : adminUsers.findPermissionCodes(adminId);
        List<String> roles = adminUsers.findRoleNames(adminId);
        return new AdminProfile(admin.getId(), admin.getUsername(), admin.getEmail(), admin.getFullName(),
                admin.isSuperAdmin(), roles, authorities);
    }

    /** Effective permission codes for the current admin; {@code ["*"]} means a super-admin (all). */
    @Transactional(readOnly = true)
    public List<String> myPermissions(UUID adminId) {
        AdminUser admin = adminUsers.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        return admin.isSuperAdmin() ? List.of("*") : adminUsers.findPermissionCodes(adminId);
    }

    @Transactional
    public void changePassword(UUID adminId, String currentPassword, String newPassword, String ip, String userAgent) {
        AdminUser admin = adminUsers.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        admin.changePassword(passwordEncoder.encode(newPassword));
        adminUsers.save(admin);
        refreshTokenService.revokeAllForAdmin(adminId); // force re-login on all devices
        notificationService.enqueue(new SendEmailCommand("ADMIN_PASSWORD_CHANGED", admin.getEmail(),
                Map.of("name", admin.getFullName())));
        audit.record(adminId, "PASSWORD_CHANGED", null, ip, userAgent);
    }
}
