package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.worker.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Basic profile fields only - designations/skills have their own dedicated assignment endpoints. */
@Schema(description = "Replaces a worker's core profile fields. Designations/skills are not part of "
        + "this request - use the dedicated assignment endpoints below instead.")
public record UpdateWorkerRequest(
        @Schema(example = "Ramesh") @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Kumar") @Size(max = 100) String middleName,
        @Schema(example = "Sharma") @NotBlank @Size(max = 100) String lastName,
        @Schema(description = "Self-reported gender.") @NotNull Gender gender,
        @Schema(example = "1995-08-15") @Past LocalDate dateOfBirth,
        @Schema(description = "Must be unique among active (non-deleted) workers, excluding this "
                + "worker's own current row.", example = "9876543210")
        @NotBlank @Size(max = 20) String mobileNumber,
        @Schema(example = "ramesh.kumar@example.com") @Email @Size(max = 191) String personalEmail,
        @Schema(description = "Id of an existing department (Masterdata module); must exist.",
                example = "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d")
        @NotNull UUID departmentId,
        @Schema(description = "Optional industry id (Masterdata module); when given, must exist.",
                example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", nullable = true)
        UUID industryId
) {
}
