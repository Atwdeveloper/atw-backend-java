package in.anytimework.atwbackend.shared.kernel;

/**
 * Simple active/inactive state for admin-managed reference data (industry, designation, skill,
 * department, ...). A named enum rather than a boolean, matching how {@code AdminUser}/
 * {@code AdminStatus} already models account status - leaves room for a third state later
 * (e.g. PENDING_REVIEW) without a schema change, and reads better in queries/logs than a bare flag.
 */
public enum ActiveStatus {
    ACTIVE,
    INACTIVE
}
