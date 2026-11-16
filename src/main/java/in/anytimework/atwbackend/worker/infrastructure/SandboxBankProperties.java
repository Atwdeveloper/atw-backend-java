package in.anytimework.atwbackend.worker.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for Sandbox.co.in's bank-account-verification product, bound from
 * {@code atw.bank-verification.sandbox.*}. Kept independent of the {@code kyc} module's own
 * {@code SandboxKycProperties} (Aadhaar e-KYC) even though both may end up pointing at the same
 * Sandbox.co.in account in practice - this module's bank verification and kyc's Aadhaar e-KYC are two
 * separately swappable vendor relationships (repo CLAUDE.md's vendor-independence rule), not one.
 *
 * <p><strong>Unlike every other vendor adapter in this codebase, this one's exact wire contract
 * (endpoint path, field names) was NOT ported from a working legacy reference.</strong> The legacy app
 * used a different vendor (ICICI) for bank verification, and that integration was dead/stubbed code -
 * not a working example to copy. {@link SandboxBankVerificationAdapter} is instead a best-effort
 * reading of Sandbox.co.in's publicly documented penny-drop API, which could not be confirmed current
 * against a live instance at the time this was written. <strong>Confirm the real endpoint/field names
 * against Sandbox.co.in's own current API reference before ever setting
 * {@code atw.bank-verification.provider=sandbox} anywhere beyond a throwaway test call.</strong></p>
 */
@ConfigurationProperties(prefix = "atw.bank-verification.sandbox")
public record SandboxBankProperties(String baseUrl, String apiKey, String apiSecret, Duration timeout) {

    public SandboxBankProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.sandbox.co.in";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
    }
}
