package in.anytimework.atwbackend.company.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Small reference to a company's OWNER account, embedded in {@link CompanyResponse}. */
@Schema(description = "Small reference to a company's OWNER account.")
public record CompanyOwnerRef(UUID id, String name, String email) {
}
