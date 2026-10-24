package in.anytimework.atwbackend.identity.application.dto;

import java.util.UUID;

/**
 * Outcome of a password login: either an OTP challenge (2FA pending) or, when OTP is disabled,
 * the issued tokens directly.
 *
 * @param testOtp the plaintext OTP code, set only when {@code atw.admin.security.otp-in-response}
 *                is on (local/test only, enforced by StartupSecurityValidator) - lets local dev
 *                exercise the login flow without a real SMTP provider configured. Always null otherwise.
 */
public record LoginResult(boolean otpRequired, UUID challengeId, AuthTokens tokens, String testOtp) {

    public static LoginResult otpChallenge(UUID challengeId, String testOtp) {
        return new LoginResult(true, challengeId, null, testOtp);
    }

    public static LoginResult authenticated(AuthTokens tokens) {
        return new LoginResult(false, null, tokens, null);
    }
}
