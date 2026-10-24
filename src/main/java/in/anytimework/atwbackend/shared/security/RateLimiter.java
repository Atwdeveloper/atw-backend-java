package in.anytimework.atwbackend.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window request counter backed by Redis, so the limit holds across every application
 * instance (not just in-memory per-replica), which is what actually matters once this runs as more
 * than one Azure Container Apps replica.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Counts this call against {@code key}'s window and returns whether it's still within
     * {@code maxAttempts}.
     *
     * <p>{@code DataAccessException} (Redis unreachable, timed out, etc.) is caught here
     * deliberately: this runs inside {@link AuthRateLimitFilter}, ahead of the servlet dispatcher,
     * so an uncaught exception here would bypass {@code GlobalExceptionHandler} entirely and
     * surface as a raw container error page on every call to login/verify-otp/resend-otp/
     * forgot-password/reset-password/accept-invite - a Redis blip would otherwise take down
     * authentication itself, which is a far worse outcome than the rate limit silently not being
     * enforced for the duration of that blip.</p>
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        String redisKey = "ratelimit:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= maxAttempts;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for rate limiting (key={}); failing open for this request.", key, e);
            return true;
        }
    }
}
