package in.anytimework.atwbackend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single {@link Clock} bean so time is injected, never read via {@code Instant.now()} statically.
 * This keeps time-dependent logic unit-testable (swap in a fixed clock in tests).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
