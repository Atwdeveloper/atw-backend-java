package in.anytimework.atwbackend.identity.domain;

/** Account state of an admin user. Only ACTIVE accounts may authenticate. */
public enum AdminStatus {
    ACTIVE,
    DISABLED
}
