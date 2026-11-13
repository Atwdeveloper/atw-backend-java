package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A staff member's contact address. Every field is `null` until explicitly set via Update "
        + "Staff Address - there is no row (and no 404) for a staff member who has never had an address saved; "
        + "this response simply comes back all-null instead. Stored as plain text, not encrypted - a deliberate, "
        + "developer-confirmed choice (2026-11-13) treating a free-text address as lower-sensitivity than the "
        + "Aadhaar/PAN/bank-account data in the KYC and Bank Detail sub-resources below, which stay encrypted.")
public record AdminUserAddressResponse(
        @Schema(description = "Address line 1.", example = "221B Test Street", nullable = true)
        String line1,

        @Schema(description = "Address line 2.", nullable = true)
        String line2,

        @Schema(description = "State id - a foreign key into Masterdata > Geography > List States, not a free-text name.",
                example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", nullable = true)
        UUID stateId,

        @Schema(description = "City id - a foreign key into Masterdata > Geography > List Cities (district-grain; "
                + "ATW's product vocabulary calls it \"city\"), scoped under `stateId` above.",
                example = "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01", nullable = true)
        UUID cityId,

        @Schema(description = "Postal/PIN code. No format validation.", example = "302001", nullable = true)
        String pincode,

        @Schema(description = "Groundwork for a future geocoding integration. Always `null` today - not yet "
                + "settable through this API.", nullable = true)
        Double latitude,

        @Schema(description = "Groundwork for a future geocoding integration. Always `null` today - not yet "
                + "settable through this API.", nullable = true)
        Double longitude,

        @Schema(description = "Groundwork for a future geocoding integration. Always `null` today - not yet "
                + "settable through this API.", nullable = true)
        String formattedAddress
) {
}
