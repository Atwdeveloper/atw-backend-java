package in.anytimework.atwbackend.company.domain;

/**
 * The role a {@link CompanyUser} holds within its own company. Only {@link #OWNER} is ever created
 * as of this slice - {@link #STAFF} is modeled now (so the column/enum never needs a breaking change
 * later) but no flow creates one yet; confirmed out of scope for this round.
 *
 * <p>Named {@code CompanyUserRole}, not the bare {@code CompanyRole} - deliberately, so that name
 * stays free for a possible future, richer company-side permission-profile concept, which this is
 * not.</p>
 */
public enum CompanyUserRole {
    OWNER,
    STAFF
}
