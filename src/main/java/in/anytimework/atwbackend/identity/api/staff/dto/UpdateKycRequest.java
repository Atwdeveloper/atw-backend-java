package in.anytimework.atwbackend.identity.api.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * All fields optional - captured progressively. Deliberately no format/checksum validation
 * (Aadhaar/PAN/etc. patterns): only a length cap, to avoid rejecting legitimate input on a
 * guessed-at pattern. Values are encrypted at rest - see AdminUserKyc / PiiEncryptor.
 */
@Schema(description = "Full replace (not a merge) of a staff member's government-ID KYC numbers - creates the row "
        + "on first write. All fields optional, captured progressively. No separate write permission beyond "
        + "`admin_staff.edit` - only *reading* unmasked values needs the extra `admin_staff.view_kyc` permission; "
        + "note that even the admin who just wrote a value gets it back masked in the response if they don't also "
        + "hold that permission.")
public record UpdateKycRequest(
        @Schema(description = "Aadhaar number. Encrypted at rest. No checksum/format validation beyond the length "
                + "cap, deliberately - avoids rejecting legitimate input against a guessed-at 12-digit pattern that "
                + "might not match real-world data exactly.", example = "123456789012", nullable = true)
        @Size(max = 20) String aadhaarNumber,

        @Schema(description = "PAN number. Same rationale as `aadhaarNumber` for the lack of checksum validation.",
                example = "ABCDE1234F", nullable = true)
        @Size(max = 20) String panNumber,

        @Schema(description = "UAN (Universal Account Number, EPFO).", nullable = true)
        @Size(max = 20) String uanNumber,

        @Schema(description = "PF (Provident Fund) number.", nullable = true)
        @Size(max = 50) String pfNumber,

        @Schema(description = "ESIC number.", nullable = true)
        @Size(max = 30) String esicNumber
) {
}
