package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSkillCategoryRequest(
        @Schema(description = "Skill category id to assign to this designation. Required - to clear the "
                + "assignment instead, use the Clear Skill Category endpoint, not this one with a null body.",
                example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a")
        @NotNull UUID skillCategoryId
) {
}
