package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Upgrades an already-existing worker's KYC using a successful Aadhaar e-KYC "
        + "verification - the same mechanism Create Worker uses when aadhaarVerificationId is given at "
        + "creation time, exposed separately for a worker who was onboarded manually and now wants to "
        + "move to a live, API-verified KYC record.")
public record CompleteAadhaarVerificationRequest(
        @Schema(description = "Id of an already-completed Aadhaar e-KYC verification (kyc module). "
                + "Must not already be consumed, expired, or still pending.",
                example = "0e1f2a3b-4c5d-4e6f-9a7b-8c9d0e1f2a3b") @NotNull UUID verificationId
) {
}
