package in.anytimework.atwbackend.identity.application.dto;

import java.util.UUID;

/** @param testOtp see {@link LoginResult#testOtp()} - same local/test-only shortcut. */
public record ResendOtpResult(UUID challengeId, String testOtp) {
}
