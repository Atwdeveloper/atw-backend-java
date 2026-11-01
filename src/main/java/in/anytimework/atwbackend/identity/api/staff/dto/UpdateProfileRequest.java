package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.Gender;

import java.time.LocalDate;

/** All fields optional - profile data is captured progressively, not necessarily all at once. */
public record UpdateProfileRequest(
        LocalDate dateOfBirth,
        Gender gender
) {
}
