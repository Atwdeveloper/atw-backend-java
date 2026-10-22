package in.anytimework.atwbackend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs after bearer-token authentication and enforces per-request session validity beyond plain
 * JWT signature/expiry checks:
 *
 * <ul>
 *     <li>the token's baked-in security version ({@code sv} claim) must still match the admin's
 *     current version — a password reset/change bumps the version server-side, which immediately
 *     revokes every access token issued before it, instead of waiting out the token's natural
 *     expiry;</li>
 *     <li>the admin must still be {@code ACTIVE};</li>
 *     <li>when the token says the admin must change their password, only the change-password and
 *     "me" endpoints are reachable — everything else is blocked until that happens.</li>
 * </ul>
 *
 * <p>Looks up current admin state through {@link AdminSecurityStateProvider}, a port implemented by
 * the {@code identity} module, so this class (in {@code shared.security}) never depends on
 * {@code identity}'s repositories/entities directly.</p>
 */
@Component
public class AdminSessionPolicyFilter extends OncePerRequestFilter {

    private static final String PUBLIC_AUTH_PREFIX = "/api/admin/auth/";
    private static final Set<String> ALLOWED_WHILE_MUST_CHANGE_PASSWORD = Set.of(
            "/api/admin/account/change-password",
            "/api/admin/account/me"
    );

    private final AdminSecurityStateProvider stateProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminSessionPolicyFilter(AdminSecurityStateProvider stateProvider, ObjectMapper objectMapper, Clock clock) {
        this.stateProvider = stateProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(PUBLIC_AUTH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt token = jwtAuthentication.getToken();
        UUID adminId = UUID.fromString(token.getSubject());
        // Deliberately not a direct cast to Integer: JSON number claims can come back as Integer or
        // Long depending on the decoder's numeric parsing, and a raw cast risks a ClassCastException
        // on every authenticated request if that guess is wrong.
        Number svClaim = token.getClaim("sv");
        int tokenSecurityVersion = svClaim == null ? -1 : svClaim.intValue();
        boolean mustChangePassword = Boolean.TRUE.equals(token.getClaimAsBoolean("must_change_password"));

        Optional<AdminSecurityStateProvider.AdminSecurityState> state = stateProvider.find(adminId);
        if (state.isEmpty() || !state.get().active() || state.get().securityVersion() != tokenSecurityVersion) {
            SecurityContextHolder.clearContext();
            ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "SESSION_REVOKED",
                    "This session is no longer valid. Please sign in again.", objectMapper, clock);
            return;
        }

        if (mustChangePassword && !ALLOWED_WHILE_MUST_CHANGE_PASSWORD.contains(path)) {
            ErrorResponseWriter.write(response, HttpStatus.FORBIDDEN, "MUST_CHANGE_PASSWORD",
                    "You must change your password before continuing.", objectMapper, clock);
            return;
        }

        chain.doFilter(request, response);
    }
}
