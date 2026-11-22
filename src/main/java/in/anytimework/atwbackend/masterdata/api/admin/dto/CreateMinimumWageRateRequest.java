package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Always creates a new, independent version - there is no request type for editing an existing rate's value. */
public record CreateMinimumWageRateRequest(
        @Schema(description = "State this rate applies to.", example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2") @NotNull UUID stateId,
        @Schema(description = "Skill category this rate applies to.", example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a")
        @NotNull UUID skillCategoryId,
        @Schema(description = "Statutory minimum daily wage in INR. Up to 8 integer and 2 fraction digits.", example = "650.00")
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal minDailyRate,
        @Schema(description = "Date this version becomes effective. Must not collide with another ACTIVE rate "
                + "for the same state + skill category on this exact date.", example = "2026-04-01")
        @NotNull LocalDate effectiveFrom
) {
}
