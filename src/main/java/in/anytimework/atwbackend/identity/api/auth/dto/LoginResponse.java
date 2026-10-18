package in.anytimework.atwbackend.identity.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;

import java.util.UUID;

/**
 * Login result: when {@code otpRequired} is true, {@code challengeId} is set and the client must call
 * verify-otp; when false (OTP disabled), {@code tokens} is populated directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(boolean otpRequired, UUID challengeId, AuthTokens tokens) {

    public static LoginResponse otp(UUID challengeId) {
        return new LoginResponse(true, challengeId, null);
    }

    public static LoginResponse authenticated(AuthTokens tokens) {
        return new LoginResponse(false, null, tokens);
    }
}
