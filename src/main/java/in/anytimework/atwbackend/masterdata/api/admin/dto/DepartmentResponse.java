package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record DepartmentResponse(
        @Schema(description = "Department id.", example = "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d") UUID id,
        String name,
        @Schema(description = "ACTIVE or INACTIVE.", example = "ACTIVE") ActiveStatus status
) {
}
