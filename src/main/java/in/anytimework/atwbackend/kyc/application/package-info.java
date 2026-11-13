/**
 * Public API of the kyc module (Spring Modulith named interface "api"): other modules call
 * {@code KycService} to submit a worker's KYC numbers or complete it from a live Aadhaar e-KYC
 * verification, and keep only the UUIDs/DTOs it hands back. {@code KycServiceImpl}/
 * {@code IdentityNumberHasher} are package-private and remain inaccessible across module boundaries, the
 * same shape as {@code document.application}/{@code notification.application}; {@code WorkerIntakeService}
 * is public (only {@code kyc.api.admin}, within this same module, calls it today) - see its own
 * Javadoc.
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.kyc.application;
