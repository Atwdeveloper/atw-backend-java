package in.anytimework.atwbackend.identity.api.staff.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** All fields optional - captured progressively, same as the rest of the staff profile. */
public record UpdateAddressRequest(
        @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        UUID stateId,
        UUID cityId,
        @Size(max = 10) String pincode
) {
}
