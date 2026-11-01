package in.anytimework.atwbackend.identity.api.staff.dto;

import java.util.UUID;

public record AdminUserAddressDto(
        String line1,
        String line2,
        UUID stateId,
        UUID cityId,
        String pincode,
        Double latitude,
        Double longitude,
        String formattedAddress
) {
}
