package in.anytimework.atwbackend.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Against real local Redis: counts within a window, blocks once the cap is reached. */
@SpringBootTest
@ActiveProfiles("test")
class RateLimiterTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void allowsUpToTheCapThenBlocksWithinTheSameWindow() {
        String key = "test:" + UUID.randomUUID();

        for (int i = 1; i <= 3; i++) {
            assertThat(rateLimiter.allow(key, 3, Duration.ofMinutes(1))).as("attempt %d", i).isTrue();
        }
        assertThat(rateLimiter.allow(key, 3, Duration.ofMinutes(1))).as("4th attempt, over the cap").isFalse();
    }

    @Test
    void differentKeysHaveIndependentCounters() {
        String keyA = "test:" + UUID.randomUUID();
        String keyB = "test:" + UUID.randomUUID();

        assertThat(rateLimiter.allow(keyA, 1, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimiter.allow(keyA, 1, Duration.ofMinutes(1))).isFalse();
        // keyB's counter is unaffected by keyA already being at its cap.
        assertThat(rateLimiter.allow(keyB, 1, Duration.ofMinutes(1))).isTrue();
    }
}
