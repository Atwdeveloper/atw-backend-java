package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A worker's permanent or current address (whichever {type} was requested). "
        + "When no address of that type has been saved yet, every field is returned as null - this is "
        + "not a 404, since the sub-resource simply doesn't exist yet.")
public record WorkerAddressResponse(
        @Schema(example = "221B, Civil Lines") String line1,
        @Schema(example = "Near City Hospital") String line2,
        @Schema(description = "State id (Masterdata module).",
                example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", nullable = true) UUID stateId,
        @Schema(description = "City/district id (Masterdata module).",
                example = "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01", nullable = true) UUID cityId,
        @Schema(example = "302001") String pincode,
        @Schema(description = "Geocoded from the address when available; null otherwise.", nullable = true)
        Double latitude,
        @Schema(description = "Geocoded from the address when available; null otherwise.", nullable = true)
        Double longitude,
        @Schema(description = "Geocoder-formatted, human-readable address string when available; null "
                + "otherwise.", nullable = true) String formattedAddress
) {
}
