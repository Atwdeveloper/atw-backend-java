package in.anytimework.atwbackend.notification.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.anytimework.atwbackend.notification.application.SmsMessage;
import in.anytimework.atwbackend.notification.application.SmsSender;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.UUID;

/**
 * Calls the real Exotel SMS API - the legacy app's own OTP/SMS vendor (see {@code SettingHelper::sendOtpSms}
 * in the old Laravel app). Active only when {@code atw.sms.provider=exotel} is explicitly set; the
 * default remains {@link LogSmsSender} ({@code matchIfMissing=true}), so this adapter never runs in
 * tests or default local dev regardless of whether Exotel credentials happen to be configured.
 *
 * <p>Response parsing ({@code SMSMessage.Sid}/{@code .Status}, requesting JSON via the {@code .json}
 * URL suffix) is taken from Exotel's public API docs (developer.exotel.com), not independently
 * verified against a live send - if the real response shape differs, the fix is confined entirely to
 * this class's private {@link ExotelSendResponse} record (same caveat as {@code PythonFaceAuthAdapter}).
 * Legacy itself never parsed a response at all (pure fire-and-forget); this is a deliberate improvement
 * for traceability, not a requirement - a failure to parse the response still only logs a fallback id,
 * it never fails the send that already reached Exotel successfully.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.sms.provider", havingValue = "exotel")
class ExotelSmsSender implements SmsSender {

    private final RestClient restClient;
    private final ExotelSmsProperties properties;

    ExotelSmsSender(RestClient exotelRestClient, ExotelSmsProperties properties) {
        this.restClient = exotelRestClient;
        this.properties = properties;
    }

    @Override
    public String send(SmsMessage message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", properties.senderId());
        form.add("To", withCountryCode(message.toMobileNumber()));
        form.add("Body", message.body());
        form.add("DltTemplateId", properties.dltTemplateId());

        ExotelSendResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/Accounts/{sid}/Sms/send.json", properties.sid())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(ExotelSendResponse.class);
        } catch (ResourceAccessException e) {
            String causeMessage = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                    .toLowerCase(Locale.ROOT);
            if (causeMessage.contains("timed out") || causeMessage.contains("timeout")) {
                throw new BusinessRuleException("SMS provider timed out - please try again.");
            }
            throw new BusinessRuleException("SMS provider is unreachable - please try again shortly.");
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("SMS provider rejected the request: " + e.getStatusText());
        }

        if (response != null && response.smsMessage() != null && response.smsMessage().sid() != null) {
            return response.smsMessage().sid();
        }
        return "exotel-" + UUID.randomUUID();
    }

    /** Exotel expects a 91-prefixed Indian mobile number - same normalization legacy's sendOtpSms() applied. */
    private static String withCountryCode(String mobileNumber) {
        return mobileNumber.startsWith("91") ? mobileNumber : "91" + mobileNumber;
    }

    private record ExotelSendResponse(@JsonProperty("SMSMessage") SmsMessageWire smsMessage) {
    }

    private record SmsMessageWire(@JsonProperty("Sid") String sid, @JsonProperty("Status") String status) {
    }
}
