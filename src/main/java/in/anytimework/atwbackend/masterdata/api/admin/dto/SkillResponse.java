package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SkillResponse(
        @Schema(description = "Skill id.", example = "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a") UUID id,
        String name,
        @Schema(description = "Id of the designation this skill belongs to.", example = "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b")
        UUID designationId,
        @Schema(description = "Display name of the owning designation, resolved server-side so callers don't "
                + "need a second lookup.", example = "Mason")
        String designationName,
        @Schema(description = "ACTIVE or INACTIVE.", example = "ACTIVE") ActiveStatus status,
        @Schema(description = "Short-lived (~15 minute) signed download URL for this skill's icon, or null "
                + "when none is set. Always null in the List response - only GET /{id} resolves a real value.",
                nullable = true)
        String imageUrl
) {
}
