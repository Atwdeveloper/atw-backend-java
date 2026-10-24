package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.Gender;

import java.time.LocalDate;

public record AdminUserProfileDto(LocalDate dateOfBirth, Gender gender, String addressLine, String pincode) {
}
