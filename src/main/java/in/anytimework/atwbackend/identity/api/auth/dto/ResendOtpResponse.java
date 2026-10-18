package in.anytimework.atwbackend.identity.api.auth.dto;

import java.util.UUID;

/** Returned by resend-otp: the new challenge id to use for verify-otp. */
public record ResendOtpResponse(UUID challengeId) {
}
