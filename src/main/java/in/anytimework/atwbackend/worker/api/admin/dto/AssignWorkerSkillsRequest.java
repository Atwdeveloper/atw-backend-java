package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

/** Full replace: the worker holds exactly these skills afterward. Each must belong to one of the worker's current designations. */
@Schema(description = "Full replacement of a worker's skills.")
public record AssignWorkerSkillsRequest(
        @Schema(description = "Each skill id (Masterdata module) must belong to one of the worker's "
                + "CURRENT designations (see Assign Designations above). Omit/empty clears every skill.",
                nullable = true, example = "[\"9d0e1f2a-3b4c-4d5e-8f6a-7b8c9d0e1f2a\"]")
        Set<UUID> skillIds
) {

    public AssignWorkerSkillsRequest {
        if (skillIds == null) {
            skillIds = Set.of();
        }
    }
}
