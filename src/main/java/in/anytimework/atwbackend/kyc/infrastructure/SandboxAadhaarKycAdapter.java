package in.anytimework.atwbackend.kyc.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.anytimework.atwbackend.kyc.application.AadhaarKycPort;
import in.anytimework.atwbackend.kyc.application.AadhaarVerifiedDetails;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;

/**
 * Calls the real Sandbox.co.in Aadhaar OKYC e-KYC API - the legacy app's own vendor (see
 * {@code SandboxKycService} in the old Laravel app, bound via {@code AadhaarKycServiceInterface}).
 * Active only when {@code atw.aadhaar-kyc.provider=sandbox} is explicitly set; the default remains
 * {@link StubAadhaarKycAdapter} ({@code matchIfMissing=true}), so this adapter never runs in tests or
 * default local dev.
 *
 * <p><strong>Re-authenticates on every call, no token caching</strong> - mirrors legacy's own
 * {@code SandboxKycService::getToken()}, which is likewise called fresh inside every
 * {@code sendAadhaarOtp}/{@code verifyAadhaarOtp}. This is a deliberate choice to match a proven
 * reference rather than introduce new token-expiry-tracking state: Aadhaar e-KYC is an inherently
 * low-frequency, human-paced operation (already rate-limited at {@code WorkerIntakeService}'s layer),
 * so the extra round-trip per call is not a real performance concern.</p>
 *
 * <p>Wire-format field names and the "please wait N seconds" cooldown shape are taken from legacy's
 * calling code, not independently verified against a live Sandbox.co.in response - if the real
 * contract differs, the fix is confined entirely to this class's private wire-format records (same
 * caveat as {@code PythonFaceAuthAdapter}). Notably uncertain: {@code date_of_birth}'s exact format:
 * tries UIDAI's standard {@code dd-MM-yyyy} first, then ISO {@code yyyy-MM-dd} as a fallback, before
 * failing loudly rather than silently mis-parsing a worker's date of birth.</p>
 *
 * <p>Maps onto today's existing {@link AadhaarVerifiedDetails} contract only - deliberately does NOT
 * capture {@code care_of}/father's name or the granular address breakdown (house/street/landmark/
 * locality/vtc/subdistrict/post_office) Sandbox.co.in's response also includes, even though legacy
 * captures some of those: confirmed with the developer 2026-11-11 as a gap not being filled this
 * round. {@code addressLine1} is Sandbox's own pre-formatted {@code full_address} string (the same
 * field legacy's own top-level {@code 'address'} key uses), not a hand-assembled concatenation of the
 * granular fields.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.aadhaar-kyc.provider", havingValue = "sandbox")
class SandboxAadhaarKycAdapter implements AadhaarKycPort {

    private static final DateTimeFormatter DOB_PRIMARY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);
    private static final String OTP_REQUEST_REASON = "Worker Registration KYC";

    private final RestClient restClient;
    private final SandboxKycProperties properties;

    SandboxAadhaarKycAdapter(RestClient sandboxRestClient, SandboxKycProperties properties) {
        this.restClient = sandboxRestClient;
        this.properties = properties;
    }

    @Override
    public String sendOtp(String aadhaarNumber) {
        String token = authenticate();
        OtpSendResponse response;
        try {
            response = restClient.post()
                    .uri("/kyc/aadhaar/okyc/otp")
                    .header("Authorization", token)
                    .header("x-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OtpSendRequest("in.co.sandbox.kyc.aadhaar.okyc.otp.request", aadhaarNumber, "Y", OTP_REQUEST_REASON))
                    .retrieve()
                    .body(OtpSendResponse.class);
        } catch (ResourceAccessException e) {
            throw unreachable(e);
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("Aadhaar e-KYC provider rejected the OTP request: " + e.getStatusText());
        }

        String referenceId = response != null && response.data() != null ? response.data().referenceId() : null;
        if (referenceId == null || referenceId.isBlank()) {
            throw new BusinessRuleException(firstNonBlank(
                    response != null && response.data() != null ? response.data().message() : null,
                    response != null ? response.message() : null,
                    "Aadhaar e-KYC provider did not return a reference id."));
        }
        return referenceId;
    }

    @Override
    public AadhaarVerifiedDetails verifyOtp(String referenceId, String otp) {
        String token = authenticate();
        OtpVerifyResponse response;
        try {
            response = restClient.post()
                    .uri("/kyc/aadhaar/okyc/otp/verify")
                    .header("Authorization", token)
                    .header("x-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OtpVerifyRequest("in.co.sandbox.kyc.aadhaar.okyc.request", referenceId, otp))
                    .retrieve()
                    .body(OtpVerifyResponse.class);
        } catch (ResourceAccessException e) {
            throw unreachable(e);
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("Invalid or expired OTP");
        }

        VerifiedDataWire data = response != null ? response.data() : null;
        if (data == null || !"VALID".equalsIgnoreCase(data.status())) {
            throw new BusinessRuleException("Invalid or expired OTP");
        }

        AddressWire address = data.address();
        return new AadhaarVerifiedDetails(
                data.name(),
                data.gender(),
                parseDob(data.dateOfBirth()),
                data.fullAddress(),
                null,
                address != null ? address.pincode() : null,
                address != null ? address.state() : null,
                address != null ? address.district() : null,
                decodePhoto(data.photo()));
    }

    /** Legacy sets the raw token string directly as the Authorization header value - no "Bearer " prefix. */
    private String authenticate() {
        AuthResponse response;
        try {
            response = restClient.post()
                    .uri("/authenticate")
                    .header("x-api-key", properties.apiKey())
                    .header("x-api-secret", properties.apiSecret())
                    .header("x-api-version", "1.0.0")
                    .retrieve()
                    .body(AuthResponse.class);
        } catch (ResourceAccessException e) {
            throw unreachable(e);
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("Unable to authenticate with the Aadhaar e-KYC provider.");
        }
        String token = response != null && response.data() != null ? response.data().accessToken() : null;
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Unable to authenticate with the Aadhaar e-KYC provider.");
        }
        return token;
    }

    private static BusinessRuleException unreachable(ResourceAccessException e) {
        String message = String.valueOf(
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                .toLowerCase(Locale.ROOT);
        if (message.contains("timed out") || message.contains("timeout")) {
            return new BusinessRuleException("Aadhaar e-KYC provider timed out - please try again.");
        }
        return new BusinessRuleException("Aadhaar e-KYC provider is unreachable - please try again shortly.");
    }

    private static LocalDate parseDob(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("Aadhaar e-KYC provider did not return a date of birth.");
        }
        try {
            return LocalDate.parse(raw, DOB_PRIMARY_FORMAT);
        } catch (DateTimeParseException primaryFailure) {
            try {
                return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException fallbackFailure) {
                throw new BusinessRuleException("Aadhaar e-KYC provider returned an unrecognized date of birth format.");
            }
        }
    }

    /** The photo is optional/best-effort - never fail an otherwise-successful verification just because it didn't decode. */
    private static byte[] decodePhoto(String base64Photo) {
        if (base64Photo == null || base64Photo.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64Photo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record AuthResponse(AuthDataWire data) {
    }

    private record AuthDataWire(@JsonProperty("access_token") String accessToken) {
    }

    private record OtpSendRequest(
            @JsonProperty("@entity") String entity,
            @JsonProperty("aadhaar_number") String aadhaarNumber,
            String consent,
            String reason
    ) {
    }

    private record OtpSendResponse(String message, OtpSendDataWire data) {
    }

    private record OtpSendDataWire(@JsonProperty("reference_id") String referenceId, String message) {
    }

    private record OtpVerifyRequest(
            @JsonProperty("@entity") String entity,
            @JsonProperty("reference_id") String referenceId,
            String otp
    ) {
    }

    private record OtpVerifyResponse(VerifiedDataWire data) {
    }

    private record VerifiedDataWire(
            String name,
            String gender,
            @JsonProperty("date_of_birth") String dateOfBirth,
            String photo,
            String status,
            @JsonProperty("full_address") String fullAddress,
            AddressWire address
    ) {
    }

    private record AddressWire(String state, String district, String pincode) {
    }
}
