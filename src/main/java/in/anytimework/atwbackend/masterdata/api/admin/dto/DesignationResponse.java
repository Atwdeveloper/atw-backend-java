package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record DesignationResponse(
        @Schema(description = "Designation id.", example = "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b") UUID id,
        String name,
        @Schema(description = "ACTIVE or INACTIVE.", example = "ACTIVE") ActiveStatus status,
        @Schema(description = "Short-lived (~15 minute) signed download URL for this designation's icon, or "
                + "null when none is set. Always null in the List response - only GET /{id} resolves a real "
                + "value.", nullable = true)
        String imageUrl,
        @Schema(description = "Every industry this designation belongs to (always at least one).")
        List<IndustrySummary> industries,
        @Schema(description = "Assigned skill category id, or null if none is assigned. Set/cleared only via "
                + "the dedicated assign/clear skill-category endpoints - never via the general Update.",
                nullable = true, example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a")
        UUID skillCategoryId
) {
}
