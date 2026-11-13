package in.anytimework.atwbackend.kyc.infrastructure;

import in.anytimework.atwbackend.kyc.application.AadhaarVerifiedDetails;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * Unit tests for the one piece of this feature with a genuinely unverified external contract (see the
 * adapter's own Javadoc) - exercises the two-step authenticate-then-call flow, the no-"Bearer "-prefix
 * Authorization header legacy's own client used, and the dd-MM-yyyy/ISO date-of-birth fallback parsing.
 */
class SandboxAadhaarKycAdapterTest {

    // No .requestFactory(...) call - would silently discard the mock request factory MockRestServiceServer
    // installs on the builder, same gotcha PythonFaceAuthAdapterTest documents.
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://sandbox.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SandboxKycProperties properties =
            new SandboxKycProperties("http://sandbox.test", "api-key", "api-secret", Duration.ofSeconds(5));
    private final SandboxAadhaarKycAdapter adapter = new SandboxAadhaarKycAdapter(builder.build(), properties);

    private void expectAuthentication() {
        server.expect(requestTo("http://sandbox.test/authenticate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "api-key"))
                .andExpect(header("x-api-secret", "api-secret"))
                .andRespond(withSuccess("""
                        {"data":{"access_token":"tok-123"}}
                        """, MediaType.APPLICATION_JSON));
    }

    @Test
    void sendOtp_onSuccess_authenticatesThenReturnsReferenceId() {
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "tok-123"))
                .andRespond(withSuccess("""
                        {"data":{"reference_id":"ref-456","message":"OTP sent successfully."}}
                        """, MediaType.APPLICATION_JSON));

        String referenceId = adapter.sendOtp("999988887777");

        assertThat(referenceId).isEqualTo("ref-456");
        server.verify();
    }

    @Test
    void sendOtp_whenReferenceIdMissing_throwsWithProviderMessage() {
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp"))
                .andRespond(withSuccess("""
                        {"data":{"message":"Please wait 30 seconds before requesting a new OTP."}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.sendOtp("999988887777"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Please wait 30 seconds");
    }

    @Test
    void sendOtp_whenAuthenticationFails_throwsABusinessRuleException() {
        server.expect(requestTo("http://sandbox.test/authenticate"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> adapter.sendOtp("999988887777"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sendOtp_onConnectionFailure_throwsABusinessRuleException() {
        server.expect(requestTo("http://sandbox.test/authenticate"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> adapter.sendOtp("999988887777"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void verifyOtp_onSuccess_parsesAllFieldsIncludingBase64Photo() {
        byte[] photoBytes = {1, 2, 3, 4};
        String photoBase64 = Base64.getEncoder().encodeToString(photoBytes);
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp/verify"))
                .andExpect(header("Authorization", "tok-123"))
                .andRespond(withSuccess("""
                        {"data":{"name":"Ramesh Kumar","gender":"M","date_of_birth":"15-08-1995",
                         "photo":"%s","status":"VALID","full_address":"123 Main St, Delhi",
                         "address":{"state":"Delhi","district":"New Delhi","pincode":"110001"}}}
                        """.formatted(photoBase64), MediaType.APPLICATION_JSON));

        AadhaarVerifiedDetails details = adapter.verifyOtp("ref-456", "123456");

        assertThat(details.fullName()).isEqualTo("Ramesh Kumar");
        assertThat(details.gender()).isEqualTo("M");
        assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1995, 8, 15));
        assertThat(details.addressLine1()).isEqualTo("123 Main St, Delhi");
        assertThat(details.addressLine2()).isNull();
        assertThat(details.pincode()).isEqualTo("110001");
        assertThat(details.stateName()).isEqualTo("Delhi");
        assertThat(details.districtName()).isEqualTo("New Delhi");
        assertThat(details.photo()).isEqualTo(photoBytes);
    }

    @Test
    void verifyOtp_whenDateOfBirthIsIsoFormat_fallsBackSuccessfully() {
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp/verify"))
                .andRespond(withSuccess("""
                        {"data":{"name":"Test","gender":"F","date_of_birth":"1995-08-15",
                         "status":"VALID","full_address":"addr","address":{}}}
                        """, MediaType.APPLICATION_JSON));

        AadhaarVerifiedDetails details = adapter.verifyOtp("ref-456", "123456");

        assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1995, 8, 15));
    }

    @Test
    void verifyOtp_whenStatusIsNotValid_throwsInvalidOtp() {
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp/verify"))
                .andRespond(withSuccess("""
                        {"data":{"status":"INVALID"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.verifyOtp("ref-456", "000000"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }

    @Test
    void verifyOtp_onHttpErrorStatus_throwsInvalidOtp() {
        expectAuthentication();
        server.expect(requestTo("http://sandbox.test/kyc/aadhaar/okyc/otp/verify"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.verifyOtp("ref-456", "000000"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
