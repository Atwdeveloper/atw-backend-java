package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleDto;
import in.anytimework.atwbackend.identity.api.role.dto.UpdateAdminRoleRequest;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AdminPermission;
import in.anytimework.atwbackend.identity.domain.AdminPermissionRepository;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for roles and their permission sets. Authorization ({@code admin_roles.view/create/edit/delete})
 * is enforced at the controller via {@code @PreAuthorize}, matching how every other endpoint in
 * this module does it - this service assumes its caller is already authorized.
 */
@Service
public class AdminRoleService {

    private final AdminRoleRepository roles;
    private final AdminPermissionRepository permissions;
    private final AdminUserRepository adminUsers;
    private final AdminAuditService audit;
    private final CurrentAdmin currentAdmin;
    private final PermissionEscalationGuard escalationGuard;

    public AdminRoleService(AdminRoleRepository roles, AdminPermissionRepository permissions, AdminUserRepository adminUsers,
                       AdminAuditService audit, CurrentAdmin currentAdmin, PermissionEscalationGuard escalationGuard) {
        this.roles = roles;
        this.permissions = permissions;
        this.adminUsers = adminUsers;
        this.audit = audit;
        this.currentAdmin = currentAdmin;
        this.escalationGuard = escalationGuard;
    }

    @Transactional(readOnly = true)
    public List<AdminRoleDto> list() {
        return roles.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AdminRoleDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public AdminRoleDto create(CreateAdminRoleRequest request, String ip, String userAgent) {
        if (roles.existsByName(request.name())) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        // Resolve (validates the codes actually exist) before the escalation check below, so a
        // typo'd code reports as "unknown" rather than a confusing "you don't hold this [made-up]
        // permission" from the escalation guard.
        Set<AdminPermission> resolved = resolvePermissions(request.permissionCodes());
        // Not just defense for AdminStaffService's role-assignment guard: without this, someone holding
        // admin_roles.create/admin_roles.edit but not admin_staff.edit could define a role with permissions beyond
        // their own, then edit it onto an *already-assigned* role later with no new "assignment"
        // event for AdminStaffService to intercept - see PermissionEscalationGuard's Javadoc.
        escalationGuard.requireWithinOwnPermissions(codesOf(resolved));
        AdminRole role = new AdminRole(request.name(), request.description(), false);
        role.replacePermissions(resolved);
        role = roles.save(role);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_ROLE_CREATED", role.getId().toString(), ip, userAgent);
        return toDto(role);
    }

    @Transactional
    public AdminRoleDto update(UUID id, UpdateAdminRoleRequest request, String ip, String userAgent) {
        AdminRole role = findOrThrow(id);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("System roles cannot be modified.");
        }
        if (roles.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("A role named '" + request.name() + "' already exists.");
        }
        Set<AdminPermission> resolved = resolvePermissions(request.permissionCodes());
        escalationGuard.requireWithinOwnPermissions(codesOf(resolved));
        role.rename(request.name(), request.description());
        role.replacePermissions(resolved);
        role = roles.save(role);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_ROLE_UPDATED", role.getId().toString(), ip, userAgent);
        return toDto(role);
    }

    @Transactional
    public void delete(UUID id, String ip, String userAgent) {
        AdminRole role = findOrThrow(id);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("System roles cannot be deleted.");
        }
        long assignedCount = adminUsers.countByRolesId(id);
        if (assignedCount > 0) {
            throw new ConflictException("Cannot delete role: " + assignedCount
                    + " staff member(s) are currently assigned to it. Reassign them first.");
        }
        roles.delete(role);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_ROLE_DELETED", id.toString(), ip, userAgent);
    }

    private Set<AdminPermission> resolvePermissions(List<String> codes) {
        if (codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> requested = new HashSet<>(codes);
        List<AdminPermission> found = permissions.findAllByCodeIn(requested);
        if (found.size() != requested.size()) {
            Set<String> foundCodes = new HashSet<>();
            found.forEach(p -> foundCodes.add(p.getCode()));
            List<String> unknown = requested.stream().filter(code -> !foundCodes.contains(code)).toList();
            throw new ValidationException("Unknown permission code(s): " + String.join(", ", unknown));
        }
        return new HashSet<>(found);
    }

    private static Set<String> codesOf(Set<AdminPermission> permissions) {
        return permissions.stream().map(AdminPermission::getCode).collect(Collectors.toSet());
    }

    private AdminRole findOrThrow(UUID id) {
        return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private AdminRoleDto toDto(AdminRole role) {
        List<String> codes = role.getPermissions().stream().map(AdminPermission::getCode).sorted().toList();
        return new AdminRoleDto(role.getId(), role.getName(), role.getDescription(), role.isSystemRole(), codes);
    }
}
