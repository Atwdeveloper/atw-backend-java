package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.worker.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * designationIds/primaryDesignationId are required at creation (a worker's trade is fundamental
 * classification, not progressively-captured data) - up to 3, exactly one marked primary, enforced
 * in WorkerService. skillIds is optional: a worker may not have specific skills recorded yet.
 * Address/bank-detail are deliberately NOT part of this request - separate follow-up calls, matching
 * how AdminStaff's own satellite data is captured.
 *
 * <p>{@code aadhaarVerificationId} is optional: when present (the "add worker via Aadhaar" path - see
 * {@code AdminWorkerIntakeController}), {@code WorkerService.create()} consumes that already-succeeded
 * e-KYC verification to mark this worker's KYC VERIFIED and apply its captured photo, failing worker
 * creation entirely if the verification is missing, not yet verified, expired, or already used. Left
 * null for the plain manual-entry path, where KYC starts NOT_STARTED as before.</p>
 */
@Schema(description = "Creates a worker with their core profile plus initial designation(s)/skill(s). "
        + "The resulting worker always starts at status REGISTERED (never settable directly).")
public record CreateWorkerRequest(
        @Schema(example = "Ramesh") @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Kumar") @Size(max = 100) String middleName,
        @Schema(example = "Sharma") @NotBlank @Size(max = 100) String lastName,
        @Schema(description = "Self-reported gender.") @NotNull Gender gender,
        @Schema(example = "1995-08-15") @Past LocalDate dateOfBirth,
        @Schema(description = "Must be unique among active (non-deleted) workers; a soft-deleted "
                + "worker's old number becomes available for reuse.", example = "9876543210")
        @NotBlank @Size(max = 20) String mobileNumber,
        @Schema(example = "ramesh.kumar@example.com") @Email @Size(max = 191) String personalEmail,
        @Schema(description = "Id of an existing department (Masterdata module); must exist.",
                example = "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d")
        @NotNull UUID departmentId,
        @Schema(description = "Optional industry id (Masterdata module); when given, must exist.",
                example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c", nullable = true)
        UUID industryId,
        @Schema(description = "1 to 3 designation ids (Masterdata module); every id must exist.",
                example = "[\"7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e\"]")
        @NotEmpty Set<UUID> designationIds,
        @Schema(description = "Must be one of designationIds - the worker's primary trade.",
                example = "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e")
        @NotNull UUID primaryDesignationId,
        @Schema(description = "Optional. Each skill must belong to one of the selected designationIds.",
                nullable = true)
        Set<UUID> skillIds,
        @Schema(description = "Optional. Id of an already-completed Aadhaar e-KYC verification (kyc "
                + "module) to adopt at creation time - marks this worker's KYC VERIFIED immediately and "
                + "copies its captured photo as the profile photo. Fails the whole creation if the "
                + "verification is missing, not yet verified, expired, or already consumed. Omit for "
                + "plain manual entry, where KYC starts NOT_STARTED.", nullable = true)
        UUID aadhaarVerificationId
) {
    public CreateWorkerRequest {
        if (skillIds == null) {
            skillIds = Set.of();
        }
    }

    /** Plain manual-entry convenience: no Aadhaar verification behind this creation. */
    public CreateWorkerRequest(String firstName, String middleName, String lastName, Gender gender,
                               LocalDate dateOfBirth, String mobileNumber, String personalEmail,
                               UUID departmentId, UUID industryId, Set<UUID> designationIds,
                               UUID primaryDesignationId, Set<UUID> skillIds) {
        this(firstName, middleName, lastName, gender, dateOfBirth, mobileNumber, personalEmail,
                departmentId, industryId, designationIds, primaryDesignationId, skillIds, null);
    }
}
