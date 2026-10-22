package in.anytimework.atwbackend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Port through which {@code shared.security} checks whether an admin's session is still valid
 * (current security version, active status) without depending on the {@code identity} module's
 * repositories/entities directly.
 *
 * <p>{@code identity} already depends on {@code shared} (for security/exception infrastructure), so
 * the reverse dependency is not allowed — it would create a module cycle. The {@code identity}
 * module implements this interface and supplies it as a bean; {@code shared.security} only ever
 * sees this abstraction, keeping the module graph acyclic.</p>
 */
public interface AdminSecurityStateProvider {

    Optional<AdminSecurityState> find(UUID adminId);

    record AdminSecurityState(int securityVersion, boolean active) {
    }
}
