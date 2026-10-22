package in.anytimework.atwbackend.identity.application.dto;

import java.util.UUID;

/**
 * Outcome of a password login: either an OTP challenge (2FA pending) or, when OTP is disabled,
 * the issued tokens directly.
 */
public record LoginResult(boolean otpRequired, UUID challengeId, AuthTokens tokens) {

    public static LoginResult otpChallenge(UUID challengeId) {
        return new LoginResult(true, challengeId, null);
    }

    public static LoginResult authenticated(AuthTokens tokens) {
        return new LoginResult(false, null, tokens);
    }
}
