package in.anytimework.atwbackend.company.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Replaces a company's name/industry only - GST/PAN (own verification lifecycle), status (own guarded transition), and addresses (own sub-resource) are never editable here. */
@Schema(description = "Replaces a company's name/industry only.")
public record UpdateCompanyRequest(
        @Schema(example = "Acme Builders Pvt Ltd") @NotBlank @Size(max = 255) String name,
        @Schema(example = "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c") @NotNull UUID industryId
) {
}
