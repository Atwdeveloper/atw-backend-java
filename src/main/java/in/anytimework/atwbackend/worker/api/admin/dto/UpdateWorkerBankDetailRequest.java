package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Replaces the worker's bank/payout detail. All fields optional, captured "
        + "progressively. accountNumber is stored encrypted at rest; see WorkerBankDetailResponse for "
        + "how it's masked on read.")
public record UpdateWorkerBankDetailRequest(
        @Schema(example = "Ramesh Kumar Sharma") @Size(max = 150) String accountHolderName,
        @Schema(example = "State Bank of India") @Size(max = 150) String bankName,
        @Schema(description = "Stored encrypted at rest.", example = "33412345678") @Size(max = 50) String accountNumber,
        @Schema(example = "SBIN0001234") @Size(max = 15) String ifscCode,
        @Schema(example = "Jaipur Civil Lines Branch") @Size(max = 150) String branchName
) {
}
