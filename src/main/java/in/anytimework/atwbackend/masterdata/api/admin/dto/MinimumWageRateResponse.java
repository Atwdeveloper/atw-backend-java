package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MinimumWageRateResponse(
        @Schema(description = "Rate row id.", example = "4a5b6c7d-8e9f-4a1b-9c2d-3e4f5a6b7c8d") UUID id,
        @Schema(description = "State id this rate applies to.", example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2") UUID stateId,
        @Schema(description = "Resolved state name, for convenience.", example = "Rajasthan") String stateName,
        @Schema(description = "Skill category id this rate applies to.", example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a")
        UUID skillCategoryId,
        @Schema(description = "Resolved skill category name, for convenience.", example = "Skilled") String skillCategoryName,
        @Schema(description = "Statutory minimum daily wage in INR.", example = "650.00") BigDecimal minDailyRate,
        @Schema(description = "Date this version becomes/became effective. Rows are immutable - a correction is "
                + "always a new row with its own effectiveFrom, never an edit of this field.", example = "2026-04-01")
        LocalDate effectiveFrom,
        @Schema(description = "ACTIVE (currently in force) or INACTIVE (retracted, e.g. entered in error).", example = "ACTIVE")
        ActiveStatus status
) {
}
