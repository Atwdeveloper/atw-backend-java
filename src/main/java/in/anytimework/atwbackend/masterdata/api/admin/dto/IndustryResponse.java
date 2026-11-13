package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record IndustryResponse(
        @Schema(description = "Industry id.", example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c") UUID id,
        String name,
        @Schema(description = "Backend-generated, immutable. Format `IND` + a 7-digit sequence; never user-supplied.", example = "IND0000001")
        String code,
        @Schema(description = "ACTIVE or INACTIVE.", example = "ACTIVE") ActiveStatus status,
        @Schema(description = "Short-lived (~15 minute) signed download URL for this industry's icon, or null "
                + "when none is set. Always null in the List response regardless of whether an icon exists "
                + "(resolving it there would be expensive at scale) - only GET /{id} resolves a real value.",
                nullable = true)
        String imageUrl
) {
}
