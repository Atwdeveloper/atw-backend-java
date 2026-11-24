package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** No skillCategoryId here - that's set via the dedicated assign/clear endpoints, see DesignationService. */
@Schema(description = "No `skillCategoryId` field here by design - whatever is currently assigned is left "
        + "completely untouched by this call. Use the dedicated assign/clear skill-category endpoints to "
        + "change it.")
public record UpdateDesignationRequest(
        @Schema(example = "Mason (Skilled)") @NotBlank @Size(max = 150) String name,
        @Schema(description = "Full replacement of this designation's industry ids. Must contain at least one id.",
                example = "[\"6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c\"]")
        @NotEmpty Set<UUID> industryIds,
        @Schema(description = "Which one of industryIds is this designation's primary industry after this "
                + "update. Required on every update, even one that doesn't change the industry set - must be a "
                + "member of industryIds, or this is rejected with 400.",
                example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c")
        @NotNull UUID primaryIndustryId
) {
}
