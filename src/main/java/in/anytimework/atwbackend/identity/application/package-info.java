/**
 * Public API of the identity module (Spring Modulith named interface "api"): other modules depending
 * on an admin's identity (existence, role, active status, etc.) should go through a type in this
 * package. Nothing currently does - this module has no cross-module callers yet - but declaring the
 * boundary now, before one exists, is what already protects {@code kyc}/{@code document}/
 * {@code notification}/{@code masterdata.application.lookup}; without it, a future module could
 * silently reach straight into {@code identity.domain.AdminUserRepository} with no build-time warning.
 *
 * <p>Every current service here (e.g. {@code AdminStaffService}, {@code AdminAuditService}) is
 * {@code public} purely so sibling {@code identity.api.*} controllers can call it within this module -
 * not because each one is meant to be a deliberate cross-module contract. If/when another module
 * genuinely needs to call into identity, prefer carving out a narrow, purpose-built sub-package for
 * just that need (mirroring {@code masterdata.application.lookup}'s split from its own admin-CRUD
 * services) rather than depending on this whole package's broad surface.</p>
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.identity.application;
