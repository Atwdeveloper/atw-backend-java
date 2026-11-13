package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.SmsMessage;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit tests for the one piece of this feature with a genuinely unverified external contract (see the adapter's own Javadoc). */
class ExotelSmsSenderTest {

    // No .requestFactory(...) call - would silently discard the mock request factory MockRestServiceServer
    // installs on the builder, same gotcha PythonFaceAuthAdapterTest documents.
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://exotel.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ExotelSmsProperties properties = new ExotelSmsProperties(
            "key", "token", "sid123", "exotel.test", "ATWOTP", "dlt456", Duration.ofSeconds(5));
    private final ExotelSmsSender sender = new ExotelSmsSender(builder.build(), properties);

    @Test
    void send_onSuccess_sendsExpectedFormBodyAndParsesSidFromResponse() {
        server.expect(requestTo("http://exotel.test/v1/Accounts/sid123/Sms/send.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("From=ATWOTP&To=919876543210&Body=TestBody&DltTemplateId=dlt456"))
                .andRespond(withSuccess("""
                        {"SMSMessage":{"Sid":"sms-abc","Status":"queued"}}
                        """, MediaType.APPLICATION_JSON));

        String id = sender.send(new SmsMessage("9876543210", "TestBody"));

        assertThat(id).isEqualTo("sms-abc");
        server.verify();
    }

    @Test
    void send_whenMobileAlreadyHas91Prefix_doesNotDoublePrefix() {
        server.expect(requestTo("http://exotel.test/v1/Accounts/sid123/Sms/send.json"))
                .andExpect(content().string("From=ATWOTP&To=919876543210&Body=TestBody&DltTemplateId=dlt456"))
                .andRespond(withSuccess("{\"SMSMessage\":{\"Sid\":\"x\"}}", MediaType.APPLICATION_JSON));

        sender.send(new SmsMessage("919876543210", "TestBody"));

        server.verify();
    }

    @Test
    void send_whenResponseHasNoSid_fallsBackToASyntheticId() {
        server.expect(requestTo("http://exotel.test/v1/Accounts/sid123/Sms/send.json"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String id = sender.send(new SmsMessage("9876543210", "TestBody"));

        assertThat(id).startsWith("exotel-");
    }

    @Test
    void send_onHttpErrorStatus_throwsABusinessRuleException() {
        server.expect(requestTo("http://exotel.test/v1/Accounts/sid123/Sms/send.json"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> sender.send(new SmsMessage("9876543210", "TestBody")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void send_onConnectionFailure_throwsABusinessRuleException() {
        server.expect(requestTo("http://exotel.test/v1/Accounts/sid123/Sms/send.json"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> sender.send(new SmsMessage("9876543210", "TestBody")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
