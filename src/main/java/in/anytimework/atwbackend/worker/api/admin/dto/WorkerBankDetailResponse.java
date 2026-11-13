package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** accountNumber is masked (e.g. "**********1234") unless the caller holds kyc.view_sensitive - same sensitivity gate as the KYC numbers, since a bank account number is comparably identifying. */
@Schema(description = "A worker's bank/payout detail. When none has been saved yet, every field is "
        + "returned as null/false - not a 404, since the sub-resource simply doesn't exist yet.")
public record WorkerBankDetailResponse(
        @Schema(example = "Ramesh Kumar Sharma") String accountHolderName,
        @Schema(example = "State Bank of India") String bankName,
        @Schema(description = "Masked (e.g. \"**********1234\") unless the caller also holds "
                + "kyc.view_sensitive, in which case the full decrypted number is returned - the same "
                + "sensitivity gate used for the Aadhaar/PAN numbers in the kyc module.",
                example = "**********5678") String accountNumber,
        @Schema(example = "SBIN0001234") String ifscCode,
        @Schema(example = "Jaipur Civil Lines Branch") String branchName,
        @Schema(description = "Not modifiable through this endpoint - reflects verification performed "
                + "elsewhere, if any.") boolean verified,
        @Schema(nullable = true) Instant verifiedAt
) {
}
