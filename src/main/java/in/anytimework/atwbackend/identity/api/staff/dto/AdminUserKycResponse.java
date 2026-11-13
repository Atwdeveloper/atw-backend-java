package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Values are masked (e.g. "********1234") unless the caller holds admin_staff.view_kyc - see AdminStaffKycService. */
@Schema(description = "A staff member's government-ID KYC numbers. Every field is `null` until explicitly set via "
        + "Update Staff KYC - no row, no 404, just an all-null response. All 5 values are encrypted at rest "
        + "(AES-256-GCM, random IV per value) independently of the masking described below.")
public record AdminUserKycResponse(
        @Schema(description = "Aadhaar number. Masked to all but the last 4 characters (e.g. `\"********9012\"`, or "
                + "fully masked if 4 characters or shorter) unless the caller additionally holds `admin_staff.view_kyc` "
                + "- by default only the Senior Developer and Junior Developer seed roles hold that. Masking applies "
                + "purely to this response; the stored value is unaffected either way.",
                example = "123456789012", nullable = true)
        String aadhaarNumber,

        @Schema(description = "PAN number. Same masking rule as `aadhaarNumber`.", example = "ABCDE1234F", nullable = true)
        String panNumber,

        @Schema(description = "UAN (Universal Account Number, EPFO). Same masking rule as `aadhaarNumber`.", nullable = true)
        String uanNumber,

        @Schema(description = "PF (Provident Fund) number. Same masking rule as `aadhaarNumber`.", nullable = true)
        String pfNumber,

        @Schema(description = "ESIC number. Same masking rule as `aadhaarNumber`.", nullable = true)
        String esicNumber
) {
}
