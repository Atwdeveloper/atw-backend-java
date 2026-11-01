package in.anytimework.atwbackend.masterdata.application.lookup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MinimumWageRateSummary(UUID id, BigDecimal minDailyRate, LocalDate effectiveFrom) {
}
