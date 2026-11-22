package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full single-company detail. gstNumber/panNumber come back masked (see shared.security.PiiMasking) unless the caller holds company.view_sensitive - decided server-side in CompanyService, not by this DTO. */
@Schema(description = "Full company detail. gstNumber/panNumber come back masked unless the caller holds company.view_sensitive.")
public record CompanyResponse(
        UUID id,
        String companyCode,
        String name,
        UUID industryId,
        @Schema(nullable = true) String gstNumber,
        @Schema(nullable = true) String panNumber,
        @Schema(nullable = true) Instant gstVerifiedAt,
        @Schema(nullable = true) String gstVerificationRef,
        CompanyStatus status,
        @Schema(nullable = true) String statusReason,
        @Schema(nullable = true) CompanyOwnerRef owner,
        List<CompanyAddressResponse> addresses,
        Instant createdAt
) {
}
