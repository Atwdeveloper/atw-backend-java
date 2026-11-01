package in.anytimework.atwbackend.masterdata.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Always creates a new, independent version - there is no request type for editing an existing rate's value. */
public record CreateMinimumWageRateRequest(
        @NotNull UUID stateId,
        @NotNull UUID skillCategoryId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal minDailyRate,
        @NotNull LocalDate effectiveFrom
) {
}
