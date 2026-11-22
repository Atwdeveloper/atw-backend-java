package in.anytimework.atwbackend.masterdata.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No {@code code} field - generated server-side (see {@code IndustryService.generateIndustryCode}), never user-supplied. */
@Schema(description = "No `code` field here by design - it is generated server-side (format `IND` + a 7-digit "
        + "sequence) and returned in the response; it is never accepted from the client.")
public record CreateIndustryRequest(
        @Schema(example = "Construction") @NotBlank @Size(max = 150) String name
) {
}
