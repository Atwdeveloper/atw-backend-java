package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record BulkUpdateDepartmentStatusRequest(
        @Schema(description = "Department ids to update. Each is processed independently; an unknown id is "
                + "reported in the response's `failures` list rather than failing the whole batch.")
        @NotEmpty Set<UUID> ids,
        @Schema(description = "Target status to apply to every id in the batch.", example = "INACTIVE")
        @NotNull ActiveStatus status
) {
}
