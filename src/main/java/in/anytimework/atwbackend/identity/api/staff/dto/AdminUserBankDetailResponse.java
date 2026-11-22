package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** accountNumber is masked (e.g. "**********1234") unless the caller holds admin_staff.view_bank_details. */
@Schema(description = "A staff member's bank/payout details. Every field is `null` until explicitly set via "
        + "Update Staff Bank Detail - no row, no 404, just an all-null response. `accountNumber` is encrypted at "
        + "rest (AES-256-GCM) independently of the masking described below.")
public record AdminUserBankDetailResponse(
        @Schema(description = "Account holder's name. Never masked.", example = "Staff Holder", nullable = true)
        String accountHolderName,

        @Schema(description = "Bank name. Never masked.", example = "Test Bank", nullable = true)
        String bankName,

        @Schema(description = "Bank account number. Masked to all but the last 4 characters (e.g. `\"********2333\"`, "
                + "or fully masked if 4 characters or shorter) unless the caller additionally holds "
                + "`admin_staff.view_bank_details` - by default only the Senior Developer and Junior Developer seed "
                + "roles hold that. Masking applies purely to this response; the stored value is unaffected either way.",
                example = "000111222333", nullable = true)
        String accountNumber,

        @Schema(description = "IFSC code. Never masked, no format validation.", example = "TEST0001234", nullable = true)
        String ifscCode,

        @Schema(description = "Branch name. Never masked.", example = "MG Road", nullable = true)
        String branchName
) {
}
