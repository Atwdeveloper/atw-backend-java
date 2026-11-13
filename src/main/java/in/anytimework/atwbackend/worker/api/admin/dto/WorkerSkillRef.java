package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "One of a worker's assigned skills (Masterdata module), denormalized with its "
        + "display name for convenience.")
public record WorkerSkillRef(
        @Schema(example = "9d0e1f2a-3b4c-4d5e-8f6a-7b8c9d0e1f2a") UUID id,
        @Schema(example = "Wall Plastering") String name
) {
}
