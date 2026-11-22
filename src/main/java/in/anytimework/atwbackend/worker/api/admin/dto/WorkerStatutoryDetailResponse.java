package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code uanNumber}/{@code esicNumber} are masked to the last 4 characters unless the caller also
 * holds {@code kyc.view_sensitive} - the same sensitivity gate used for the Aadhaar/PAN numbers in
 * the kyc module, since these are comparably-sensitive government-issued identifiers.
 */
@Schema(description = "A worker's statutory identity numbers (UAN, ESIC). When neither has been "
        + "saved yet, both fields are null - not a 404, since the sub-resource simply doesn't exist yet.")
public record WorkerStatutoryDetailResponse(
        @Schema(description = "Universal Account Number (EPFO). Masked unless the caller also holds "
                + "kyc.view_sensitive.", example = "********2346", nullable = true) String uanNumber,
        @Schema(description = "ESIC number. Masked unless the caller also holds kyc.view_sensitive.",
                example = "********5678", nullable = true) String esicNumber
) {
}
