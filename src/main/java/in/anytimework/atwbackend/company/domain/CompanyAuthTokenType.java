package in.anytimework.atwbackend.company.domain;

/** Kind of one-time link token for a company user. Mirrors {@code identity.domain.AuthTokenType} exactly. */
public enum CompanyAuthTokenType {
    INVITE,
    PASSWORD_RESET
}
