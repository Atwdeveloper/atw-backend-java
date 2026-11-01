package in.anytimework.atwbackend.masterdata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSkillRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull UUID designationId
) {
}
