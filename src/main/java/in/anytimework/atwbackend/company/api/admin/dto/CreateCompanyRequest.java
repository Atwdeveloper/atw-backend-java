package in.anytimework.atwbackend.company.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Creates a company and, in the same transaction, its OWNER login account - only an OWNER is ever
 * created this round, never a separate "company contact email" field: the owner's own email is
 * deliberately the company's practical contact point (see {@code CompanyService.create}'s Javadoc).
 * The new company always starts at status {@code PENDING_VERIFICATION}; GST/PAN are optional and
 * unverified at creation - GST verification is a separate, later action ({@code POST .../gst-verify}),
 * never a precondition of creation, so a vendor outage can never block onboarding.
 */
@Schema(description = "Creates a company and its OWNER login account together. GST/PAN are optional and "
        + "unverified at creation - use the separate GST-verify action afterward.")
public record CreateCompanyRequest(
        @Schema(example = "Acme Builders Pvt Ltd") @NotBlank @Size(max = 255) String name,
        @Schema(description = "Industry id (Masterdata module); must reference an existing industry.",
                example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c") @NotNull UUID industryId,
        @Schema(description = "Optional. Verified later via the dedicated GST-verify action, never at creation.",
                example = "27AAAPG1234L1ZV", nullable = true) @Size(max = 20) String gstNumber,
        @Schema(description = "Optional.", example = "AAAPG1234L", nullable = true) @Size(max = 20) String panNumber,
        @Schema(description = "Optional at creation; may be empty or omitted. When non-empty, exactly one "
                + "entry must have primary=true.", nullable = true)
        List<@Valid CompanyAddressRequest> addresses,
        @Schema(example = "Ravi Shah") @NotBlank @Size(max = 150) String ownerName,
        @Schema(description = "Must be unique among active company-user accounts; becomes the owner's login "
                + "email and the address the invite link is sent to.",
                example = "ravi.shah@acmebuilders.example.com") @NotBlank @Email @Size(max = 320) String ownerEmail,
        @Schema(example = "9876543210", nullable = true) @Size(max = 20) String ownerPhone
) {
    public CreateCompanyRequest {
        if (addresses == null) {
            addresses = List.of();
        }
    }
}
