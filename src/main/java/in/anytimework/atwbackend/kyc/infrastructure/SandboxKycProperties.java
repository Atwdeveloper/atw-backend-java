package in.anytimework.atwbackend.kyc.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for Sandbox.co.in (the legacy app's Aadhaar OKYC e-KYC vendor, bound via
 * {@code AppServiceProvider::AadhaarKycServiceInterface}), bound from {@code atw.aadhaar-kyc.sandbox.*}.
 * Carried over from the legacy app's own {@code SandboxKycService}/{@code .env} - same field shape
 * (an API key + secret exchanged for a bearer token via {@code /authenticate}), confirmed with the
 * developer 2026-11-11 to reuse those exact credential values via environment variables (never
 * hardcoded here or in any committed file).
 */
@ConfigurationProperties(prefix = "atw.aadhaar-kyc.sandbox")
public record SandboxKycProperties(String baseUrl, String apiKey, String apiSecret, Duration timeout) {

    public SandboxKycProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.sandbox.co.in";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
    }
}
