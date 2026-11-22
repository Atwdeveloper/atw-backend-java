package in.anytimework.atwbackend.company.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for Sandbox.co.in's GST Compliance API (GSTIN search/verify), bound from
 * {@code atw.gst-verification.sandbox.*}. {@code apiKey}/{@code apiSecret} default to the same
 * {@code SANDBOX_API_KEY}/{@code SANDBOX_API_SECRET} environment variables the {@code kyc} module's
 * Aadhaar e-KYC product already uses (see {@code atw.aadhaar-kyc.sandbox.*} in application.properties)
 * - confirmed by directly reading the legacy app's PHP: {@code GstVerificationService} and
 * {@code SandboxKycService} (Aadhaar OKYC) call the identical {@code POST /authenticate} endpoint
 * with the identical pair of env vars, because GST verification is simply a second Sandbox.co.in
 * product under the same already-wired account - NOT the same situation as
 * {@code worker.infrastructure.SandboxBankProperties} (bank verification), which is a genuinely
 * different, separately-credentialed vendor relationship and deliberately does NOT share credentials
 * with anything.
 */
@ConfigurationProperties(prefix = "atw.gst-verification.sandbox")
public record SandboxGstProperties(String baseUrl, String apiKey, String apiSecret, Duration timeout) {

    public SandboxGstProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.sandbox.co.in";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
    }
}
