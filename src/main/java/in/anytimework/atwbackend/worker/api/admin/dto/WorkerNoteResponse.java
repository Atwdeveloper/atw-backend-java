package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One append-only admin remark about a worker.")
public record WorkerNoteResponse(
        @Schema(example = "a4b5c6d7-e8f9-4a1b-8c2d-3e4f5a6b7c8d") UUID id,
        @Schema(example = "Called to confirm availability for the Monday shift.") String note,
        @Schema(description = "Username of the admin who wrote the note.", example = "admin@anytimework.in")
        String createdBy,
        Instant createdAt
) {
}
