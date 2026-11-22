package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** All fields optional - captured progressively, same as the identity module's address update. */
@Schema(description = "Replaces the worker's address of the type given in the path ({@code PERMANENT} "
        + "or {@code CURRENT} - each is a separate row). Every field is optional and may be sent "
        + "progressively over several calls; a field left out clears to null rather than being left "
        + "unchanged, since this is a full replace of the address row, not a partial patch.")
public record UpdateWorkerAddressRequest(
        @Schema(example = "221B, Civil Lines") @Size(max = 255) String line1,
        @Schema(example = "Near City Hospital") @Size(max = 255) String line2,
        @Schema(description = "State id (Masterdata module); when given, must exist.",
                example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", nullable = true) UUID stateId,
        @Schema(description = "City/district id (Masterdata module); when given, must exist.",
                example = "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01", nullable = true) UUID cityId,
        @Schema(example = "302001") @Size(max = 10) String pincode
) {
}
