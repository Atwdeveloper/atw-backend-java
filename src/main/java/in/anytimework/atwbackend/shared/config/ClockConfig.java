package in.anytimework.atwbackend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * A single {@link Clock} bean so time is injected, never read via {@code Instant.now()} statically.
 * This keeps time-dependent logic unit-testable (swap in a fixed clock in tests).
 *
 * <p>Zoned to IST (ATW is an India-only business), not UTC: {@code Instant}-based usage elsewhere
 * (audit timestamps, token expiry) is unaffected either way since an {@code Instant} has no zone -
 * but anything that derives a business/calendar date via {@code LocalDate.now(clock)} (e.g. resolving
 * "today's" effective minimum-wage rate) needs the zone to get the right calendar day. A UTC clock
 * would report the previous day for the first ~5.5 hours after each India midnight.</p>
 */
@Configuration
public class ClockConfig {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
