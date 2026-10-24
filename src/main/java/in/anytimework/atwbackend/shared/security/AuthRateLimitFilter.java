package in.anytimework.atwbackend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;

/**
 * Per-IP request cap on the public, abuse-prone admin auth endpoints (see
 * {@link AdminAuthPaths#RATE_LIMITED}) - independent of (and running before) the existing
 * OTP-specific attempt/resend counters, which only bound abuse of one already-issued challenge,
 * not the rate of minting new login/OTP/reset/invite requests itself.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthRateLimitFilter(RateLimiter rateLimiter, AuthRateLimitProperties properties,
                               ObjectMapper objectMapper, Clock clock) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!properties.enabled() || !AdminAuthPaths.RATE_LIMITED.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = path + ":" + HttpRequests.clientIp(request);
        if (!rateLimiter.allow(key, properties.maxAttempts(), properties.window())) {
            ErrorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Please try again later.", objectMapper, clock);
            return;
        }
        chain.doFilter(request, response);
    }
}
