package in.anytimework.atwbackend.identity.infrastructure;

import in.anytimework.atwbackend.identity.domain.AdminStatus;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.shared.security.AdminSecurityStateProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements the {@code shared.security} port against this module's {@link AdminUserRepository},
 * so the session-policy filter in {@code shared.security} can check an admin's current security
 * version and active status without depending on {@code identity} directly.
 */
@Component
class JpaAdminSecurityStateProvider implements AdminSecurityStateProvider {

    private final AdminUserRepository adminUsers;

    JpaAdminSecurityStateProvider(AdminUserRepository adminUsers) {
        this.adminUsers = adminUsers;
    }

    @Override
    public Optional<AdminSecurityState> find(UUID adminId) {
        return adminUsers.findSecurityProjectionById(adminId)
                .map(p -> new AdminSecurityState(p.getSecurityVersion(), p.getStatus() == AdminStatus.ACTIVE));
    }
}
