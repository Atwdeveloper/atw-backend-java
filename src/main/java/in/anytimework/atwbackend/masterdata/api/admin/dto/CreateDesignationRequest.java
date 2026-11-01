package in.anytimework.atwbackend.masterdata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateDesignationRequest(
        @NotBlank @Size(max = 150) String name,
        @NotEmpty Set<UUID> industryIds,
        UUID skillCategoryId
) {
}
