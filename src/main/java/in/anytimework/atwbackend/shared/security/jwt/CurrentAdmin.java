package in.anytimework.atwbackend.shared.security.jwt;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Reads the currently-authenticated admin from the security context (the validated access token). */
@Component
public class CurrentAdmin {

    public Optional<UUID> id() {
        return jwt().map(Jwt::getSubject).map(UUID::fromString);
    }

    public Optional<String> email() {
        return jwt().map(j -> j.getClaimAsString("email"));
    }

    public boolean isSuperAdmin() {
        return jwt().map(j -> Boolean.TRUE.equals(j.getClaimAsBoolean("super_admin"))).orElse(false);
    }

    /** The admin id, or a failure if there is no authenticated admin. */
    public UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated admin in context"));
    }

    private Optional<Jwt> jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }
}
