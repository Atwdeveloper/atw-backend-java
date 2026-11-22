package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Schema(example = "Human Resources & Admin") @NotBlank @Size(max = 150) String name
) {
}
