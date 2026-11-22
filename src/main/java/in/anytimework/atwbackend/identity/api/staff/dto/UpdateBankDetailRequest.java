package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** All fields optional - captured progressively. accountNumber is encrypted at rest. */
@Schema(description = "Full replace (not a merge) of a staff member's bank/payout details - creates the row on "
        + "first write. All fields optional, captured progressively. No separate write permission beyond "
        + "`admin_staff.edit` - only *reading* the unmasked account number needs the extra "
        + "`admin_staff.view_bank_details` permission.")
public record UpdateBankDetailRequest(
        @Schema(description = "Account holder's name.", example = "Staff Holder", nullable = true)
        @Size(max = 150) String accountHolderName,

        @Schema(description = "Bank name.", example = "Test Bank", nullable = true)
        @Size(max = 150) String bankName,

        @Schema(description = "Bank account number. Encrypted at rest (AES-256-GCM). No checksum/format validation "
                + "beyond the length cap, deliberately - avoids rejecting legitimate input against a guessed-at pattern.",
                example = "000111222333", nullable = true)
        @Size(max = 30) String accountNumber,

        @Schema(description = "IFSC code. No format validation.", example = "TEST0001234", nullable = true)
        @Size(max = 15) String ifscCode,

        @Schema(description = "Branch name.", example = "MG Road", nullable = true)
        @Size(max = 150) String branchName
) {
}
