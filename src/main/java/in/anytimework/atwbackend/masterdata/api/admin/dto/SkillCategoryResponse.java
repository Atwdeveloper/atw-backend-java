package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record SkillCategoryResponse(
        @Schema(description = "Skill category id.", example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a") UUID id,
        String name,
        @Schema(description = "Backend-generated, immutable. Format `SKC` + a 7-digit sequence; never user-supplied.", example = "SKC0000001")
        String code,
        @Schema(description = "ACTIVE or INACTIVE.", example = "ACTIVE") ActiveStatus status,
        @Schema(description = "Every designation currently assigned to this skill category (id + name). "
                + "Empty, never null, if none are assigned.") List<DesignationSummary> designations,
        @Schema(description = "designations.size() - provided directly so the frontend never needs to "
                + "count the list itself.", example = "3") int designationCount
) {
}
