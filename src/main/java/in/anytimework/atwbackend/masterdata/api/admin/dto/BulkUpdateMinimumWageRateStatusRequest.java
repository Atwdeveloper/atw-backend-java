package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Bulk version of the existing single {@code /{id}/status} toggle - e.g. retracting several wrongly-entered rates at once. */
public record BulkUpdateMinimumWageRateStatusRequest(
        @Schema(description = "Minimum wage rate ids to update. Each is processed independently; an unknown id "
                + "is reported in the response's `failures` list rather than failing the whole batch.")
        @NotEmpty Set<UUID> ids,
        @Schema(description = "Target status to apply to every id in the batch - e.g. INACTIVE to retract "
                + "several wrongly-entered rates in one call.", example = "INACTIVE")
        @NotNull ActiveStatus status
) {
}
