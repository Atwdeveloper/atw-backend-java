package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** All fields optional - profile data is captured progressively, not necessarily all at once. */
@Schema(description = "Full replace (not a merge) of a staff member's demographic profile - creates the row on "
        + "first write. Both fields optional, captured progressively; omitting a previously-set field clears it "
        + "back to `null`. Address is not part of this body - see Get/Update Staff Address.")
public record UpdateProfileRequest(
        @Schema(description = "Date of birth, ISO-8601 date. No past/future constraint enforced.",
                example = "1995-06-15", nullable = true)
        LocalDate dateOfBirth,

        @Schema(description = "Self-reported gender.", example = "FEMALE", nullable = true)
        Gender gender
) {
}
