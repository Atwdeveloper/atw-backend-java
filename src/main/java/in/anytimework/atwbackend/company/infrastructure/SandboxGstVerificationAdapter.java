package in.anytimework.atwbackend.company.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.anytimework.atwbackend.company.application.GstVerificationPort;
import in.anytimework.atwbackend.company.application.GstVerificationResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.UUID;

/**
 * Calls Sandbox.co.in's real GST Compliance API (GSTIN search/verify) - the legacy app's own
 * {@code App\Services\GstVerificationService}, ported faithfully from its actual PHP source (not
 * guessed): same two calls, same headers, same envelope-parsing branches. Active only when
 * {@code atw.gst-verification.provider=sandbox} is explicitly set; the default remains
 * {@link StubGstVerificationAdapter} ({@code matchIfMissing=true}).
 *
 * <p><strong>Call 1 - token exchange</strong> ({@link #authenticate()}): {@code POST /authenticate}
 * with headers {@code x-api-key}/{@code x-api-secret}/{@code x-api-version: 1.0.0} (the version is a
 * literal, not configurable - legacy hardcodes it the same way). Byte-for-byte the same shape as
 * {@code kyc.infrastructure.SandboxAadhaarKycAdapter.authenticate()} (confirmed: legacy's Aadhaar
 * OKYC service has an identical {@code getToken()}), so this adapter re-authenticates on every call
 * rather than caching, for the same reason that one does - a low-frequency, human-paced admin
 * action, not a hot path.</p>
 *
 * <p><strong>Call 2 - GSTIN search</strong> ({@link #verify}): {@code POST /gst/compliance/public/gstin/search},
 * body {@code {"gstin": "..."}} (normalized upper-case/trimmed before send), headers
 * {@code Authorization: <raw token>} (legacy never prepends "Bearer "), {@code x-api-key} (resent;
 * {@code x-api-secret} is deliberately NOT resent here, matching legacy), {@code x-api-version},
 * {@code x-accept-cache: true}.</p>
 *
 * <p><strong>Response branching, in the exact order legacy checks it:</strong></p>
 * <ol>
 *   <li>{@code message} containing "invalid gstin pattern" (case-insensitive) -&gt; not verified,
 *       not an exception (a data problem, not a provider outage).</li>
 *   <li>{@code code != 200} -&gt; not verified (the provider reports some other structural failure -
 *       since this still arrived as a normal, parseable HTTP 200 response, it is not treated as
 *       "provider unreachable/rejected the request" - that bucket is reserved for the
 *       {@link ResourceAccessException}/{@link RestClientResponseException} catches below).</li>
 *   <li>{@code data.status_cd != "1"} -&gt; not found (legacy: Sandbox returns this as a normal HTTP
 *       200, not a 4xx).</li>
 *   <li>{@code data.data} (the actual record) missing -&gt; not verified.</li>
 *   <li>{@code data.data.sts} not case-insensitively "active" -&gt; not verified, with legacy's own
 *       exact message shape ("This GSTIN is not active (status: ...)").</li>
 *   <li>Otherwise verified: legal name from {@code lgnm}, state from {@code addr.stcd} (despite the
 *       name, a full state <em>name</em> string on Sandbox's response, not a numeric code - confirmed
 *       directly against the legacy code and its Postman example; do not mis-read this as the actual
 *       {@code state_code}, which legacy instead derives from the GSTIN's own leading 2 digits and
 *       this adapter's minimal {@link GstVerificationResult} does not carry at all).</li>
 * </ol>
 *
 * <p><strong>No provider-supplied reference id exists for this specific endpoint</strong> (confirmed
 * against the full legacy response-field mapping - unlike bank verification's {@code utr}, nothing
 * resembling a transaction/reference id is returned by GSTIN search). {@link #providerReference()}
 * is therefore this adapter's own locally-generated audit identifier for the verification attempt,
 * not a guess at a vendor field that was never documented to exist.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.gst-verification.provider", havingValue = "sandbox")
class SandboxGstVerificationAdapter implements GstVerificationPort {

    private static final String INVALID_PATTERN_MARKER = "invalid gstin pattern";

    private final RestClient restClient;
    private final SandboxGstProperties properties;

    SandboxGstVerificationAdapter(RestClient sandboxGstRestClient, SandboxGstProperties properties) {
        this.restClient = sandboxGstRestClient;
        this.properties = properties;
    }

    @Override
    public GstVerificationResult verify(String gstNumber) {
        String token = authenticate();
        String normalized = gstNumber.trim().toUpperCase(Locale.ROOT);

        SearchResponse response;
        try {
            response = restClient.post()
                    .uri("/gst/compliance/public/gstin/search")
                    .header("Authorization", token)
                    .header("x-api-key", properties.apiKey())
                    .header("x-api-version", "1.0.0")
                    .header("x-accept-cache", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SearchRequest(normalized))
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (ResourceAccessException e) {
            throw unreachable(e);
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("GST verification provider rejected the request: " + e.getStatusText());
        }

        return toResult(response);
    }

    private static GstVerificationResult toResult(SearchResponse response) {
        if (response == null) {
            return notVerified("GST verification provider returned an unexpected response.");
        }
        if (response.message() != null && response.message().toLowerCase(Locale.ROOT).contains(INVALID_PATTERN_MARKER)) {
            return notVerified("Invalid GSTIN format.");
        }
        if (response.code() == null || response.code() != 200) {
            return notVerified(StringUtils.hasText(response.message())
                    ? response.message() : "GST verification provider could not process this request.");
        }
        SearchDataWire data = response.data();
        if (data == null || !"1".equals(data.statusCd())) {
            String notFoundMessage = data != null && data.error() != null && StringUtils.hasText(data.error().message())
                    ? data.error().message() : "GSTIN not found.";
            return notVerified(notFoundMessage);
        }
        GstRecordWire record = data.data();
        if (record == null) {
            return notVerified("GST verification provider returned no record for this GSTIN.");
        }
        if (!"active".equalsIgnoreCase(record.sts())) {
            return notVerified("This GSTIN is not active (status: " + record.sts() + ").");
        }
        String state = record.addr() != null ? record.addr().stcd() : null;
        return new GstVerificationResult(true, record.lgnm(), state, "SANDBOX-GST-" + UUID.randomUUID(), null);
    }

    private static GstVerificationResult notVerified(String reason) {
        return new GstVerificationResult(false, null, null, null, reason);
    }

    /** Legacy sets the raw token string directly as the Authorization header value - no "Bearer " prefix. Same re-authenticate-per-call convention as SandboxAadhaarKycAdapter/SandboxBankVerificationAdapter - see this class's own Javadoc. */
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
            throw new BusinessRuleException("Unable to authenticate with the GST verification provider.");
        }
        String token = response != null && response.data() != null ? response.data().accessToken() : null;
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Unable to authenticate with the GST verification provider.");
        }
        return token;
    }

    private static BusinessRuleException unreachable(ResourceAccessException e) {
        String message = String.valueOf(
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                .toLowerCase(Locale.ROOT);
        if (message.contains("timed out") || message.contains("timeout")) {
            return new BusinessRuleException("GST verification provider timed out - please try again.");
        }
        return new BusinessRuleException("GST verification provider is unreachable - please try again shortly.");
    }

    private record AuthResponse(AuthDataWire data) {
    }

    private record AuthDataWire(@JsonProperty("access_token") String accessToken) {
    }

    private record SearchRequest(String gstin) {
    }

    private record SearchResponse(String message, Integer code, SearchDataWire data) {
    }

    private record SearchDataWire(@JsonProperty("status_cd") String statusCd, GstRecordWire data, SearchErrorWire error) {
    }

    private record SearchErrorWire(String message) {
    }

    private record GstRecordWire(String lgnm, String sts, AddrWire addr) {
    }

    /** {@code stcd} despite its name holds a full state NAME string on Sandbox's response, not a code - see this class's Javadoc. */
    private record AddrWire(String stcd) {
    }
}
