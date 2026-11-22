/**
 * Public API of the worker module (Spring Modulith named interface "api"): other modules depending on
 * a worker (existence, status, basic identity, etc.) should go through a type in this package. Nothing
 * currently does - this module has no cross-module callers yet - but declaring the boundary now, before
 * one exists, is what already protects {@code kyc}/{@code document}/{@code notification}/
 * {@code masterdata.application.lookup}; without it, a future module (e.g. {@code company},
 * {@code vacancy}) could silently reach straight into {@code worker.domain.WorkerRepository} with no
 * build-time warning.
 *
 * <p>Every current service here (e.g. {@code WorkerAddressService}, {@code WorkerBankDetailService})
 * is {@code public} purely so the sibling {@code worker.api.admin} controller can call it within this
 * module - not because each one is meant to be a deliberate cross-module contract. If/when another
 * module genuinely needs to call into worker, prefer carving out a narrow, purpose-built sub-package
 * for just that need (mirroring {@code masterdata.application.lookup}'s split from its own admin-CRUD
 * services) rather than depending on this whole package's broad surface.</p>
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.worker.application;
