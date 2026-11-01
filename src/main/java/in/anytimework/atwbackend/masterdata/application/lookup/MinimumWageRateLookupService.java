package in.anytimework.atwbackend.masterdata.application.lookup;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Public, read-only API for resolving the statutory minimum wage. Other modules (vacancy, payroll,
 * ...) depend on this narrow type, never on {@code MinimumWageRateService} directly - e.g. to
 * validate a proposed pay rate against the applicable floor for a vacancy's state/skill category.
 * Returns empty rather than throwing when nothing is configured yet for that pair - it's the
 * caller's business decision how to treat "no rate configured", not this module's.
 */
public interface MinimumWageRateLookupService {

    Optional<MinimumWageRateSummary> resolve(UUID stateId, UUID skillCategoryId, LocalDate asOf);
}
