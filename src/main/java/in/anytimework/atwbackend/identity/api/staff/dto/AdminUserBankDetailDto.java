package in.anytimework.atwbackend.identity.api.staff.dto;

/** accountNumber is masked (e.g. "**********1234") unless the caller holds admin_staff.view_bank_details. */
public record AdminUserBankDetailDto(
        String accountHolderName,
        String bankName,
        String accountNumber,
        String ifscCode,
        String branchName
) {
}
