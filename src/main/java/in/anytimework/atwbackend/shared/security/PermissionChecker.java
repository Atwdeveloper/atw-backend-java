package in.anytimework.atwbackend.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central authorization check used from {@code @PreAuthorize("@authz.has('staff.view')")}.
 * A super-admin (carrying the {@code SUPER_ADMIN} authority) passes every check; otherwise the
 * caller must hold the exact permission code. Keeping this in one bean means the super-admin
 * bypass is defined once, not repeated in every annotation.
 */
@Component("authz")
public class PermissionChecker {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    public boolean has(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (SUPER_ADMIN.equals(value) || permissionCode.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
