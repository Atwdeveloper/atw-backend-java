package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read-only, display-resolved address embedded directly in {@link WorkerResponse} (city/state as
 * names, not ids) - for editing, use the dedicated {@code GET/PUT /{id}/address/{type}} endpoints,
 * which return {@link WorkerAddressResponse} (raw ids, for a form to resubmit) instead.
 */
@Schema(description = "A worker's permanent or current address, resolved for display (city/state as "
        + "names, not ids). Null if no address of that type has been saved yet.")
public record WorkerAddressView(
        @Schema(example = "221B, Civil Lines") String line1,
        @Schema(example = "Near City Hospital") String line2,
        @Schema(nullable = true, example = "Jaipur") String city,
        @Schema(nullable = true, example = "Rajasthan") String state,
        @Schema(example = "302001") String pincode
) {
}
