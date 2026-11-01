package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MinimumWageRateDto(
        UUID id,
        UUID stateId,
        String stateName,
        UUID skillCategoryId,
        String skillCategoryName,
        BigDecimal minDailyRate,
        LocalDate effectiveFrom,
        ActiveStatus status
) {
}
