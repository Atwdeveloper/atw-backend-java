package in.anytimework.atwbackend.worker.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.worker.application.BankVerificationPort;
import in.anytimework.atwbackend.worker.application.BankVerificationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

/**
 * Calls Sandbox.co.in's bank-account-verification ("penny-drop") API. Active only when
 * {@code atw.bank-verification.provider=sandbox} is explicitly set; the default remains
 * {@link StubBankVerificationAdapter} ({@code matchIfMissing=true}).
 *
 * <p><strong>Best-effort wire format - see {@link SandboxBankProperties}'s Javadoc for why this
 * adapter carries materially more uncertainty than every other vendor adapter in this codebase.</strong>
 * The endpoint shape and field names below ({@code account_exists}, {@code name_at_bank}, {@code utr})
 * are read from Sandbox.co.in's own publicly documented penny-drop API, not independently verified
 * against a live instance and not ported from any working legacy reference (legacy used a different,
 * dead-code vendor integration for this). If the real contract differs, the fix is confined entirely
 * to this class's private wire-format records (same caveat as {@code PythonFaceAuthAdapter}/
 * {@code SandboxAadhaarKycAdapter}) - but confirm against Sandbox's current docs before relying on
 * this for anything beyond a throwaway test call.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.bank-verification.provider", havingValue = "sandbox")
class SandboxBankVerificationAdapter implements BankVerificationPort {

    private final RestClient restClient;
    private final SandboxBankProperties properties;

    SandboxBankVerificationAdapter(RestClient sandboxBankRestClient, SandboxBankProperties properties) {
        this.restClient = sandboxBankRestClient;
        this.properties = properties;
    }

    @Override
    public BankVerificationResult verify(String accountNumber, String ifscCode) {
        String token = authenticate();
        VerifyResponse response;
        try {
            response = restClient.get()
                    .uri("/bank/{ifsc}/accounts/{accountNumber}/verify", ifscCode, accountNumber)
                    .header("Authorization", token)
                    .header("x-api-key", properties.apiKey())
                    .retrieve()
                    .body(VerifyResponse.class);
        } catch (ResourceAccessException e) {
            throw unreachable(e);
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("Bank-verification provider rejected the request: " + e.getStatusText());
        }

        VerifyDataWire data = response != null ? response.data() : null;
        if (data == null) {
            throw new BusinessRuleException("Bank-verification provider returned an unexpected response.");
        }
        return new BankVerificationResult(data.accountExists(), data.nameAtBank(), data.utr());
    }

    /** Same re-authenticate-per-call convention as SandboxAadhaarKycAdapter - see its Javadoc. */
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
            throw new BusinessRuleException("Unable to authenticate with the bank-verification provider.");
        }
        String token = response != null && response.data() != null ? response.data().accessToken() : null;
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Unable to authenticate with the bank-verification provider.");
        }
        return token;
    }

    private static BusinessRuleException unreachable(ResourceAccessException e) {
        String message = String.valueOf(
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                .toLowerCase(Locale.ROOT);
        if (message.contains("timed out") || message.contains("timeout")) {
            return new BusinessRuleException("Bank-verification provider timed out - please try again.");
        }
        return new BusinessRuleException("Bank-verification provider is unreachable - please try again shortly.");
    }

    private record AuthResponse(AuthDataWire data) {
    }

    private record AuthDataWire(@JsonProperty("access_token") String accessToken) {
    }

    private record VerifyResponse(VerifyDataWire data) {
    }

    private record VerifyDataWire(
            @JsonProperty("account_exists") boolean accountExists,
            @JsonProperty("name_at_bank") String nameAtBank,
            String utr
    ) {
    }
}
