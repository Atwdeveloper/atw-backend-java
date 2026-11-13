package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "One of a worker's assigned designations (Masterdata module), denormalized with "
        + "its display name for convenience.")
public record WorkerDesignationRef(
        @Schema(example = "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e") UUID id,
        @Schema(example = "Mason") String name,
        @Schema(description = "True for exactly one of the worker's designations.") boolean primary
) {
}
