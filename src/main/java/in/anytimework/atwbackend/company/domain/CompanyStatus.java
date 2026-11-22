package in.anytimework.atwbackend.company.domain;

/**
 * A company's approval lifecycle (distinct from {@link CompanyUserStatus}, which is a login
 * account's own state - two unrelated concepts that deliberately stay two separate enums).
 *
 * <pre>
 * PENDING_VERIFICATION &lt;-&gt; REJECTED
 * PENDING_VERIFICATION -&gt; ACTIVE
 * ACTIVE &lt;-&gt; SUSPENDED
 * </pre>
 *
 * {@code REJECTED -> ACTIVE} is deliberately not direct - a rejected company must pass back through
 * PENDING_VERIFICATION first (a deliberate extra reconsideration step), and an ACTIVE company is
 * SUSPENDED, never "rejected" (rejection only ever applies to an application still pending). See
 * {@code CompanyService.updateStatus} for where this is enforced, and {@code Company}'s own Javadoc
 * for the critical side effect every transition into/out of ACTIVE has on this company's users.
 */
public enum CompanyStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    REJECTED
}
