package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyAddressType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** One of a company's addresses - used both nested inside {@link CreateCompanyRequest} and standalone for Add Company Address. */
@Schema(description = "One of a company's addresses.")
public record CompanyAddressRequest(
        @Schema(description = "REGISTERED_OFFICE or BRANCH.") @NotNull CompanyAddressType addressType,
        @Schema(description = "At most one address per company may be primary - setting this true demotes "
                + "whichever address currently holds it.") boolean primary,
        @Schema(example = "221B, Civil Lines") @Size(max = 255) String line1,
        @Schema(example = "Near City Hospital") @Size(max = 255) String line2,
        @Schema(description = "State id (Masterdata module); when given, must exist.", nullable = true) UUID stateId,
        @Schema(description = "City id (Masterdata module); when given, must exist.", nullable = true) UUID cityId,
        @Schema(example = "302001") @Size(max = 10) String pincode
) {
}
