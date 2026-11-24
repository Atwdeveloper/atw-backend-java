package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "One industry this designation belongs to, denormalized with its display name "
        + "and code for convenience.")
public record DesignationIndustryRef(
        @Schema(description = "Industry id.", example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c") UUID id,
        @Schema(example = "Construction") String name,
        @Schema(example = "IND0000001") String code,
        @Schema(description = "True for exactly one of this designation's industries.") boolean primary
) {
}
