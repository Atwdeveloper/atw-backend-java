package in.anytimework.atwbackend.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimiterTest} only exercises real, reachable Redis. This covers the outage path its
 * "fail open rather than take the whole API down" comment claims but never actually tested: what
 * {@link RateLimiter#allow} does when the Redis call itself throws (connection refused/timeout),
 * not just when it happens to return null.
 */
class RateLimiterRedisOutageTest {

    @Test
    @SuppressWarnings("unchecked")
    void whenRedisConnectionFails_allowFailsOpenRatherThanPropagating() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("simulated outage"));

        RateLimiter limiter = new RateLimiter(redis);

        assertThat(limiter.allow("outage-test-key", 3, Duration.ofMinutes(1))).isTrue();
    }
}
