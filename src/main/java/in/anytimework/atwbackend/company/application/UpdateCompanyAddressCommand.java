package in.anytimework.atwbackend.company.application;

import java.util.UUID;

/**
 * Full replace of one existing {@code CompanyAddress}'s mutable fields - deliberately excludes
 * {@code addressType}, which is fixed once an address row is created (delete and re-add via
 * {@link CompanyAddressInput} instead if it was mis-categorized). Part of {@link CompanyAddressService}'s
 * sub-resource CRUD - see that class's Javadoc for why addresses are a separate service rather than
 * methods on {@link CompanyService}.
 */
public record UpdateCompanyAddressCommand(
        boolean primary,
        String line1,
        String line2,
        UUID stateId,
        UUID cityId,
        String pincode
) {
}
