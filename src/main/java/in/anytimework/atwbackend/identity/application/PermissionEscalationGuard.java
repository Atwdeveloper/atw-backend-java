package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks a non-super-admin from granting (directly or via a role) a permission they don't already
 * hold themselves - the basic RBAC invariant that you can't escalate past your own authority.
 *
 * <p>This has to be enforced in two independent places, both covered by this one shared guard:
 * {@code AdminStaffService} (assigning an existing role to a staff member) and {@code AdminRoleService}
 * (defining or editing what a role itself grants). Guarding only the first would leave a real
 * escalation path open: someone holding {@code admin_roles.edit} but not {@code admin_staff.edit} could edit an
 * *already-assigned* role's permission set after the fact, with no new "assignment" event for an
 * AdminStaffService-only guard to intercept.</p>
 */
@Component
public class PermissionEscalationGuard {

    private final AdminUserRepository adminUsers;
    private final CurrentAdmin currentAdmin;

    public PermissionEscalationGuard(AdminUserRepository adminUsers, CurrentAdmin currentAdmin) {
        this.adminUsers = adminUsers;
        this.currentAdmin = currentAdmin;
    }

    /** Throws if {@code permissionCodes} contains anything the caller doesn't already hold (super-admin exempt). */
    public void requireWithinOwnPermissions(Set<String> permissionCodes) {
        if (currentAdmin.isSuperAdmin()) {
            return;
        }
        Set<String> actorPermissions = new HashSet<>(currentAdmin.id()
                .map(adminUsers::findPermissionCodes)
                .orElseGet(List::of));
        if (!actorPermissions.containsAll(permissionCodes)) {
            throw new BusinessRuleException("You cannot grant a permission you do not hold yourself.");
        }
    }
}
