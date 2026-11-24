package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** accountNumber is masked (e.g. "**********1234") unless the caller holds workers.view_bank_details - a dedicated unmask permission, independent of the kyc module's kyc.view_sensitive. */
@Schema(description = "A worker's bank/payout detail. When none has been saved yet, every field is "
        + "returned as null/false - not a 404, since the sub-resource simply doesn't exist yet.")
public record WorkerBankDetailResponse(
        @Schema(example = "Ramesh Kumar Sharma") String accountHolderName,
        @Schema(example = "State Bank of India") String bankName,
        @Schema(description = "Masked (e.g. \"**********1234\") unless the caller also holds "
                + "workers.view_bank_details, in which case the full decrypted number is returned.",
                example = "**********5678") String accountNumber,
        @Schema(example = "SBIN0001234") String ifscCode,
        @Schema(description = "Not modifiable through this endpoint - reflects verification performed "
                + "elsewhere, if any.") boolean verified,
        @Schema(nullable = true) Instant verifiedAt
) {
}
