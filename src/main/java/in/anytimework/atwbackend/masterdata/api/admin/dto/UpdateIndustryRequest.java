package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No {@code code} field - assigned once at creation, immutable thereafter (same as {@code Worker.workerCode}). */
@Schema(description = "No `code` field here - it was assigned once at creation and is immutable thereafter.")
public record UpdateIndustryRequest(
        @Schema(example = "Construction & Real Estate") @NotBlank @Size(max = 150) String name
) {
}
