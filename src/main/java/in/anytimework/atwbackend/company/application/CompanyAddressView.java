package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyAddressType;

import java.util.UUID;

/** One address, as returned to a caller - embedded (as a list) in {@link CompanyView}. stateId/cityId are raw ids, same convention as worker.application's address DTOs; resolving them to display names is left to whichever layer renders them for a human (out of scope this round - see CompanyService's class Javadoc). */
public record CompanyAddressView(
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
