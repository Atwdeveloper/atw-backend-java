package in.anytimework.atwbackend.identity.api.staff.dto;

import jakarta.validation.constraints.Size;

/**
 * All fields optional - captured progressively. Deliberately no format/checksum validation
 * (Aadhaar/PAN/etc. patterns): only a length cap, to avoid rejecting legitimate input on a
 * guessed-at pattern. Values are encrypted at rest - see AdminUserKyc / PiiEncryptor.
 */
public record UpdateKycRequest(
        @Size(max = 20) String aadhaarNumber,
        @Size(max = 20) String panNumber,
        @Size(max = 20) String uanNumber,
        @Size(max = 50) String pfNumber,
        @Size(max = 30) String esicNumber
) {
}
