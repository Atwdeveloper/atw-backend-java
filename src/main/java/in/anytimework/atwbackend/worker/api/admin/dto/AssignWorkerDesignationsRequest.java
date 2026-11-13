package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Full replace: the worker holds exactly these designations afterward. primaryDesignationId must be one of designationIds. */
@Schema(description = "Full replacement of a worker's designations. Any skill the worker currently "
        + "holds that no longer belongs to one of these designations is dropped automatically as part "
        + "of this same call.")
public record AssignWorkerDesignationsRequest(
        @Schema(description = "1 to 3 designation ids (Masterdata module); every id must exist.",
                example = "[\"7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e\"]")
        @NotEmpty Set<UUID> designationIds,
        @Schema(description = "Must be one of designationIds.", example = "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e")
        @NotNull UUID primaryDesignationId
) {
}
