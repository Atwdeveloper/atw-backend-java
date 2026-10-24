package in.anytimework.atwbackend.identity.api.staff.dto;

import jakarta.validation.constraints.Size;

/** All fields optional - captured progressively. accountNumber is encrypted at rest. */
public record UpdateBankDetailRequest(
        @Size(max = 150) String accountHolderName,
        @Size(max = 150) String bankName,
        @Size(max = 30) String accountNumber,
        @Size(max = 15) String ifscCode,
        @Size(max = 150) String branchName
) {
}
