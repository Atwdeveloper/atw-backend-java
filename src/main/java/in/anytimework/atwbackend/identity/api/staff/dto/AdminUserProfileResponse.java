package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.identity.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "A staff member's ordinary demographic profile data. Every field is `null` until explicitly "
        + "set via Update Staff Profile - no row, no 404, just an all-null response. Not sensitive; never masked, "
        + "needs no permission beyond `admin_staff.view`. Address used to live on this DTO and is now its own "
        + "sub-resource - see Get/Update Staff Address.")
public record AdminUserProfileResponse(
        @Schema(description = "Date of birth, ISO-8601 date. No past/future constraint enforced.",
                example = "1995-06-15", nullable = true)
        LocalDate dateOfBirth,

        @Schema(description = "Self-reported gender.", example = "FEMALE", nullable = true)
        Gender gender
) {
}
