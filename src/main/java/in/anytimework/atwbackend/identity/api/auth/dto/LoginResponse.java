package in.anytimework.atwbackend.identity.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Login result: when {@code otpRequired} is true, {@code challengeId} is set and the client must call
 * verify-otp; when false (OTP disabled), {@code tokens} is populated directly.
 *
 * @param testOtp the plaintext OTP, present only when {@code atw.admin.security.otp-in-response} is
 *                on (local/test only) - a dev convenience when no real SMTP provider is configured.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @Schema(description = "True when email OTP (2FA) must be completed next via Verify OTP; false "
                + "only when OTP is disabled for the environment, in which case tokens is populated "
                + "directly and no further call is needed.", example = "true")
        boolean otpRequired,

        @Schema(description = "Id of the OTP challenge just issued; pass this to Verify OTP / Resend "
                + "OTP. Present only when otpRequired=true.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
        UUID challengeId,

        @Schema(description = "The issued access/refresh token pair. Present only when otpRequired=false.",
                nullable = true)
        AuthTokens tokens,

        @Schema(description = "The OTP in plaintext - present only in local/test environments where "
                + "otp-in-response is enabled (refused at startup everywhere else). Never build "
                + "frontend behavior around this field.", example = "482913", nullable = true)
        String testOtp
) {

    public static LoginResponse otp(UUID challengeId, String testOtp) {
        return new LoginResponse(true, challengeId, null, testOtp);
    }

    public static LoginResponse authenticated(AuthTokens tokens) {
        return new LoginResponse(false, null, tokens, null);
    }
}
