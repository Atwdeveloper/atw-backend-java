package in.anytimework.atwbackend.company.domain;

/**
 * A company-user login account's own state - deliberately a different enum from
 * {@link CompanyStatus}: that one is the company record's approval lifecycle, this one is just
 * whether this particular login can authenticate right now. {@link CompanyService}'s status-change
 * cascade keeps the two in sync in one direction (a non-ACTIVE company forces every one of its
 * users to DISABLED; the company returning to ACTIVE restores them) without them ever being the same
 * underlying value.
 */
public enum CompanyUserStatus {
    ACTIVE,
    DISABLED
}
