package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateSkillRequest(
        @Schema(example = "Brick Laying (Advanced)") @NotBlank @Size(max = 150) String name,
        @Schema(description = "Designation this skill belongs to. Sending a different id than the skill's "
                + "current designation MOVES it there (and re-checks name uniqueness against the new "
                + "designation).", example = "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b")
        @NotNull UUID designationId
) {
}
