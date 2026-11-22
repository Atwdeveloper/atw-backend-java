package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyAddressType;

import java.util.UUID;

/**
 * One address supplied when creating a company - part of {@link CreateCompanyCommand}. Full address
 * editing (a separate sub-resource, mirroring how {@code worker.application.WorkerAddressService}
 * exposes {@code WorkerAddress}) is deliberately not part of this round - see {@code CompanyService}'s
 * class Javadoc.
 */
public record CompanyAddressInput(
        CompanyAddressType addressType,
        boolean primary,
        String line1,
        String line2,
        UUID stateId,
        UUID cityId,
        String pincode
) {
}
