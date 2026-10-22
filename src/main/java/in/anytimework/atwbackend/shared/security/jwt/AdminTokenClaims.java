package in.anytimework.atwbackend.shared.security.jwt;

import java.util.List;
import java.util.UUID;

/**
 * The data baked into an admin access token. {@code authorities} are the permission codes the
 * holder may exercise; super-admins carry the single {@code SUPER_ADMIN} authority instead.
 */
public record AdminTokenClaims(
        UUID adminId,
        String username,
        String email,
        String fullName,
        boolean superAdmin,
        List<String> authorities,
        List<String> roles,
        int securityVersion,
        boolean mustChangePassword
) {
}
