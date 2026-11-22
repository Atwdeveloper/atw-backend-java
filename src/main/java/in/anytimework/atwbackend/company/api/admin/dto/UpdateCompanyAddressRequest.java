package in.anytimework.atwbackend.company.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Full replace of one existing company address's fields, except {@code addressType} (fixed once created - delete and re-add via Add Company Address if it was mis-categorized). */
@Schema(description = "Replaces an existing company address.")
public record UpdateCompanyAddressRequest(
        @Schema(description = "Setting this true demotes whichever address currently holds primary.") boolean primary,
        @Schema(example = "221B, Civil Lines") @Size(max = 255) String line1,
        @Schema(example = "Near City Hospital") @Size(max = 255) String line2,
        @Schema(description = "State id (Masterdata module); when given, must exist.", nullable = true) UUID stateId,
        @Schema(description = "City id (Masterdata module); when given, must exist.", nullable = true) UUID cityId,
        @Schema(example = "302001") @Size(max = 10) String pincode
) {
}
