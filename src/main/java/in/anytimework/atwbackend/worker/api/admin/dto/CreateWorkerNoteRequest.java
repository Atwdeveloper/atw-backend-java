package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Appends one admin remark about a worker. Notes are append-only - there is no "
        + "edit or delete endpoint.")
public record CreateWorkerNoteRequest(
        @Schema(example = "Called to confirm availability for the Monday shift.")
        @NotBlank @Size(max = 2000) String note
) {
}
