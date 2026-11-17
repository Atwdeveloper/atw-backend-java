package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Account details to check against the configured bank-verification provider. "
        + "Nothing here is persisted - this is a standalone, advisory-only check (see "
        + "POST /workers/bank-detail/check).")
public record CheckBankAccountRequest(
        @NotBlank @Size(max = 50) @Schema(example = "33412345678") String accountNumber,
        @NotBlank @Size(max = 15) @Schema(example = "SBIN0001234") String ifscCode,
        @NotBlank @Size(max = 150) @Schema(example = "Ramesh Kumar Sharma") String accountHolderName
) {
}
