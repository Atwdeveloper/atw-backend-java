package in.anytimework.atwbackend.company.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Company-user authentication policy, bound from {@code atw.company.security.*}. Mirrors
 * {@code identity.application.AdminSecurityProperties}'s exact shape - its own, independent
 * properties object (not a shared/reused one) because the company-user auth flow is a deliberate,
 * independent parallel to admin auth, never an identity-module extension (this codebase's locked
 * "group by business domain, never by user type" rule).
 *
 * <p>{@code otpInResponse}/{@code fixedOtp} are the same dev/test-convenience escape hatches
 * {@code AdminSecurityProperties} has, kept for the same reason: without them, company-login
 * integration tests (part 3) would need a real email round trip. <strong>Follow-up still needed in
 * the part that builds the company login flow:</strong> {@code identity.infrastructure.StartupSecurityValidator}
 * enforces the admin equivalents of these two at boot (refusing to start if {@code otpInResponse} is
 * true, or {@code fixedOtp} is set, outside local/test/dev) - it cannot be extended to also validate
 * this class directly, because {@code identity} depending on {@code company.application} (an internal,
 * non-{@code @NamedInterface} package) would be exactly the module-boundary violation
 * {@code ModularityTests.verifiesModularStructure()} exists to catch. A separate, company-owned
 * {@code ApplicationRunner} (e.g. in {@code company.infrastructure}) should perform the same two
 * checks against this class once the company login flow actually reads them - not built yet since
 * nothing reads {@link #otpInResponse()}/{@link #fixedOtp()} until that flow exists.</p>
 */
@ConfigurationProperties(prefix = "atw.company.security")
public record CompanySecurityProperties(
        boolean otpEnabled,
        Duration otpTtl,
        int otpLength,
        int otpMaxAttempts,
        Duration otpResendInterval,
        int otpMaxResends,
        // maxFailedLogins/lockoutDuration: dormant, same as AdminSecurityProperties's own (account-level
        // lockout is not this codebase's brute-force defense - per-IP rate limiting + mandatory OTP are).
        int maxFailedLogins,
        Duration lockoutDuration,
        Duration passwordResetTtl,
        Duration inviteTtl,
        boolean otpInResponse,
        String fixedOtp
) {
}
