package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateDesignationRequest(
        @Schema(example = "Mason") @NotBlank @Size(max = 150) String name,
        @Schema(description = "Ids of every industry this designation belongs to. Must contain at least one id "
                + "- an empty array is rejected, not treated as \"no industries yet\".",
                example = "[\"6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c\"]")
        @NotEmpty Set<UUID> industryIds,
        @Schema(description = "Optional skill category to classify this designation under, used purely for "
                + "Minimum Wage Rate lookups. Omit or send null for none.", nullable = true,
                example = "8c7b6a5d-4e3f-4d2c-9b1a-7f6e5d4c3b2a")
        UUID skillCategoryId
) {
}
