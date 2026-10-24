package in.anytimework.atwbackend.identity.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Returned by resend-otp: the new challenge id to use for verify-otp.
 *
 * @param testOtp see {@link LoginResponse#testOtp()} - same local/test-only shortcut.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendOtpResponse(UUID challengeId, String testOtp) {
}
