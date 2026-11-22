package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** All fields optional - captured progressively, same as the rest of the staff profile. */
@Schema(description = "Full replace (not a merge) of a staff member's address - creates the row on first write. "
        + "Stored as plain text, not encrypted (see AdminUserAddressResponse). All fields optional - captured "
        + "progressively, same philosophy as Update Staff Profile.")
public record UpdateAddressRequest(
        @Schema(description = "Address line 1.", example = "221B Test Street", nullable = true)
        @Size(max = 255) String line1,

        @Schema(description = "Address line 2.", nullable = true)
        @Size(max = 255) String line2,

        @Schema(description = "State id - must reference an existing row from Masterdata > Geography > List States "
                + "if provided; an unknown id is `400 VALIDATION_ERROR` (\"Unknown state.\"), not silently accepted.",
                example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", nullable = true)
        UUID stateId,

        @Schema(description = "City id - must reference an existing row from Masterdata > Geography > List Cities "
                + "if provided; an unknown id is `400 VALIDATION_ERROR` (\"Unknown city.\"), not silently accepted.",
                example = "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01", nullable = true)
        UUID cityId,

        @Schema(description = "Postal/PIN code. No numeric/format check - deliberately permissive, same rationale "
                + "as the KYC fields.", example = "302001", nullable = true)
        @Size(max = 10) String pincode
) {
}
