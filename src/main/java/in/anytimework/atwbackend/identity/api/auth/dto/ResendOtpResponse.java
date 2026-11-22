package in.anytimework.atwbackend.identity.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Returned by resend-otp: the new challenge id to use for verify-otp.
 *
 * @param testOtp see {@link LoginResponse#testOtp()} - same local/test-only shortcut.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendOtpResponse(
        @Schema(description = "The new challenge id - use this (not the old one) for the next Verify OTP call.",
                example = "5c7e9f21-8b3d-4a6e-9f12-3d8b7c6e5a40")
        UUID challengeId,

        @Schema(description = "The OTP in plaintext - present only in local/test environments where "
                + "otp-in-response is enabled. Never build frontend behavior around this field.",
                example = "118302", nullable = true)
        String testOtp
) {
}
