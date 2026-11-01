package in.anytimework.atwbackend.masterdata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSkillCategoryRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 30) String code
) {
}
