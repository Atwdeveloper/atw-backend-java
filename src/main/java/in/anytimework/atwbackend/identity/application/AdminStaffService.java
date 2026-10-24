package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminRoleRef;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffRequest;
import in.anytimework.atwbackend.identity.domain.AdminStatus;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AdminAuthToken;
import in.anytimework.atwbackend.identity.domain.AdminAuthTokenRepository;
import in.anytimework.atwbackend.identity.domain.AuthTokenType;
import in.anytimework.atwbackend.identity.domain.AdminPermission;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.OpaqueTokens;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff/admin account lifecycle: list, create (with an email invite to set the first password),
 * update profile + role assignment, activate/deactivate, delete. Authorization
 * ({@code admin_staff.view/create/edit/delete/toggle_status}) is enforced at the controller via
 * {@code @PreAuthorize}, matching the rest of this module.
 */
@Service
public class AdminStaffService {

    private static final String INVITE_TEMPLATE = "ADMIN_STAFF_INVITE";

    private final AdminUserRepository adminUsers;
    private final AdminRoleRepository roles;
    private final AdminAuthTokenRepository authTokens;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AdminAuditService audit;
    private final AdminSecurityProperties properties;
    private final CurrentAdmin currentAdmin;
    private final PermissionEscalationGuard escalationGuard;
    private final Clock clock;
    private final String frontendBaseUrl;

    public AdminStaffService(AdminUserRepository adminUsers, AdminRoleRepository roles, AdminAuthTokenRepository authTokens,
                        RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                        NotificationService notificationService, AdminAuditService audit,
                        AdminSecurityProperties properties, CurrentAdmin currentAdmin,
                        PermissionEscalationGuard escalationGuard, Clock clock,
                        @Value("${atw.frontend.base-url}") String frontendBaseUrl) {
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.authTokens = authTokens;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.audit = audit;
        this.properties = properties;
        this.currentAdmin = currentAdmin;
        this.escalationGuard = escalationGuard;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStaffDto> list(Pageable pageable) {
        Page<AdminStaffDto> page = adminUsers.findAll(pageable).map(this::toDto);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public AdminStaffDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public AdminStaffDto create(CreateAdminStaffRequest request, String ip, String userAgent) {
        if (adminUsers.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An admin with this email already exists.");
        }
        if (StringUtils.hasText(request.username()) && adminUsers.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("An admin with this username already exists.");
        }
        Set<AdminRole> resolvedRoles = resolveRoles(request.roleIds());
        escalationGuard.requireWithinOwnPermissions(permissionCodesOf(resolvedRoles));

        // Random, never-disclosed password: the account cannot be used to log in until the invite
        // link (below) is used to set a real one via AuthService.acceptInvite.
        String unusedPassword = OpaqueTokens.generate();
        AdminUser admin = new AdminUser(request.username(), request.email(),
                passwordEncoder.encode(unusedPassword), request.fullName(), request.phone(), false, true);
        resolvedRoles.forEach(admin::assignRole);
        admin = adminUsers.save(admin);

        issueInvite(admin);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_CREATED", admin.getId().toString(), ip, userAgent);
        return toDto(admin);
    }

    @Transactional
    public AdminStaffDto update(UUID id, UpdateAdminStaffRequest request, String ip, String userAgent) {
        AdminUser admin = findOrThrow(id);
        if (admin.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be modified through this endpoint.");
        }
        if (StringUtils.hasText(request.username())
                && !request.username().equalsIgnoreCase(admin.getUsername())
                && adminUsers.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("An admin with this username already exists.");
        }
        Set<AdminRole> resolvedRoles = resolveRoles(request.roleIds());
        escalationGuard.requireWithinOwnPermissions(permissionCodesOf(resolvedRoles));
        boolean rolesChanged = !resolvedRoles.equals(admin.getRoles());
        admin.updateProfile(request.username(), request.fullName(), request.phone());
        admin.replaceRoles(resolvedRoles);
        if (rolesChanged) {
            // A permission REMOVAL must take effect immediately, not ride out the access token's
            // remaining TTL (up to 15 min) - the same reasoning updateStatus(DISABLED) already
            // applies, extended to cover "their role set changed" as well as "their account was
            // disabled". Gated on an actual change so a plain name/phone edit doesn't force a
            // re-login for no reason.
            admin.bumpSecurityVersion();
        }
        adminUsers.save(admin);
        if (rolesChanged) {
            refreshTokenService.revokeAllForAdmin(admin.getId());
        }
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_UPDATED", admin.getId().toString(), ip, userAgent);
        return toDto(admin);
    }

    @Transactional
    public AdminStaffDto updateStatus(UUID id, AdminStatus newStatus, String ip, String userAgent) {
        AdminUser admin = findOrThrow(id);
        guardNotSelfOrSuperAdmin(admin, "deactivate");
        admin.setStatus(newStatus);
        adminUsers.save(admin);
        if (newStatus == AdminStatus.DISABLED) {
            // Kills any outstanding refresh token immediately; the live per-request active-status
            // check (AdminSessionPolicyFilter) already rejects the current access token on its next use.
            refreshTokenService.revokeAllForAdmin(admin.getId());
        }
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_STATUS_CHANGED",
                admin.getId() + " -> " + newStatus, ip, userAgent);
        return toDto(admin);
    }

    @Transactional
    public void delete(UUID id, String ip, String userAgent) {
        AdminUser admin = findOrThrow(id);
        guardNotSelfOrSuperAdmin(admin, "delete");
        adminUsers.delete(admin);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_DELETED", id.toString(), ip, userAgent);
    }

    private static Set<String> permissionCodesOf(Set<AdminRole> rolesToAssign) {
        return rolesToAssign.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(AdminPermission::getCode)
                .collect(Collectors.toSet());
    }

    /** Blocks the two genuinely destructive actions against the bootstrap super-admin or the caller's own account. */
    private void guardNotSelfOrSuperAdmin(AdminUser target, String action) {
        if (target.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be " + action + "d through this endpoint.");
        }
        if (currentAdmin.id().map(id -> id.equals(target.getId())).orElse(false)) {
            throw new BusinessRuleException("You cannot " + action + " your own account.");
        }
    }

    private void issueInvite(AdminUser admin) {
        String token = OpaqueTokens.generate();
        authTokens.save(new AdminAuthToken(admin.getId(), OpaqueTokens.sha256Hex(token),
                AuthTokenType.INVITE, clock.instant().plus(properties.inviteTtl())));
        String inviteUrl = frontendBaseUrl + "/admin/accept-invite?token=" + token;
        notificationService.send(new SendEmailCommand(INVITE_TEMPLATE, admin.getEmail(), Map.of(
                "name", admin.getFullName(),
                "inviteUrl", inviteUrl,
                "hours", properties.inviteTtl().toHours())));
    }

    private Set<AdminRole> resolveRoles(List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<UUID> requested = new HashSet<>(roleIds);
        List<AdminRole> found = roles.findAllById(requested);
        if (found.size() != requested.size()) {
            throw new ValidationException("One or more role ids do not exist.");
        }
        return new HashSet<>(found);
    }

    private AdminUser findOrThrow(UUID id) {
        return adminUsers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    private AdminStaffDto toDto(AdminUser admin) {
        List<AdminRoleRef> roleRefs = admin.getRoles().stream()
                .map(r -> new AdminRoleRef(r.getId(), r.getName()))
                .sorted(Comparator.comparing(AdminRoleRef::name))
                .toList();
        return new AdminStaffDto(admin.getId(), admin.getUsername(), admin.getEmail(), admin.getFullName(),
                admin.getPhone(), admin.isSuperAdmin(), admin.getStatus(), roleRefs,
                admin.getLastLoginAt(), admin.getCreatedAt());
    }
}
