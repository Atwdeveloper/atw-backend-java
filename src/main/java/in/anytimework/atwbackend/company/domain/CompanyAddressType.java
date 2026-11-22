package in.anytimework.atwbackend.company.domain;

/**
 * What a {@link CompanyAddress} represents. Unlike {@code worker.domain.AddressType}
 * (exactly 2 fixed types, one row each), a company may hold any number of BRANCH rows alongside its
 * one REGISTERED_OFFICE - which one is the main one is tracked separately via
 * {@link CompanyAddress#isPrimary()}, not by this type alone.
 */
public enum CompanyAddressType {
    REGISTERED_OFFICE,
    BRANCH
}
