package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyAddressType;

import java.util.UUID;

/** stateId/cityId are raw ids, same convention as worker.application's address DTOs - resolving them to display names is left to whichever layer renders them for a human. */
public record CompanyAddressResponse(
        UUID id,
        CompanyAddressType addressType,
        boolean primary,
        String line1,
        String line2,
        UUID stateId,
        UUID cityId,
        String pincode
) {
}
