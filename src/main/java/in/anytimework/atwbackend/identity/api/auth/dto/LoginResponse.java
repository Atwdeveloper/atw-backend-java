package in.anytimework.atwbackend.identity.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;

import java.util.UUID;

/**
 * Login result: when {@code otpRequired} is true, {@code challengeId} is set and the client must call
 * verify-otp; when false (OTP disabled), {@code tokens} is populated directly.
 *
 * @param testOtp the plaintext OTP, present only when {@code atw.admin.security.otp-in-response} is
 *                on (local/test only) - a dev convenience when no real SMTP provider is configured.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(boolean otpRequired, UUID challengeId, AuthTokens tokens, String testOtp) {

    public static LoginResponse otp(UUID challengeId, String testOtp) {
        return new LoginResponse(true, challengeId, null, testOtp);
    }

    public static LoginResponse authenticated(AuthTokens tokens) {
        return new LoginResponse(false, null, tokens, null);
    }
}
