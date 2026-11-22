package in.anytimework.atwbackend.company.domain;

/**
 * Delivery channel for a company-user one-time passcode. Mirrors {@code identity.domain.OtpChannel}
 * exactly (own copy, not a cross-module reach into identity.domain - see {@link CompanyOtp}'s
 * Javadoc for why the whole company-auth chain is a deliberate, independent parallel rather than a
 * shared/reused identity type).
 */
public enum CompanyOtpChannel {
    EMAIL
}
