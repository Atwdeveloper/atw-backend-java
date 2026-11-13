package in.anytimework.atwbackend.identity.api.auth;

import in.anytimework.atwbackend.identity.api.auth.dto.AcceptInviteRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.ForgotPasswordRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.LoginRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.LoginResponse;
import in.anytimework.atwbackend.identity.api.auth.dto.LogoutRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.RefreshRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.ResendOtpRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.ResendOtpResponse;
import in.anytimework.atwbackend.identity.api.auth.dto.ResetPasswordRequest;
import in.anytimework.atwbackend.identity.api.auth.dto.VerifyOtpRequest;
import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.application.dto.ResendOtpResult;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public admin authentication endpoints (no token required). */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/auth")
@Tag(name = "Admin Auth", description = "Password login + mandatory email OTP (2FA), token "
        + "refresh/logout, and the forgot-password / reset-password / accept-invite flows. Every "
        + "endpoint in this tag is PUBLIC - no Authorization header is required, or should be sent "
        + "(these paths are explicitly permitted in SecurityConfig and exempted from the "
        + "must-change-password gate). 6 of these 8 endpoints - every one except Refresh Token and "
        + "Logout - share a per-endpoint rate limit of 10 requests/60s per client IP "
        + "(atw.admin.security.rate-limit.*); each such operation below calls that out explicitly.")
@SecurityRequirements
public class AdminAuthController {

    private static final String RATE_LIMITED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "RATE_LIMITED",
                "message": "Too many requests. Please try again later.",
                "fieldErrors": null,
                "timestamp": "2026-10-24T10:15:30Z",
                "traceId": null
              }
            }
            """;

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Log in with username/email + password (step 1 of 2)",
            description = """
                    Verifies the password only; `usernameOrEmail` is matched case-insensitively against \
                    either the admin's username or email.

                    **On success:**
                    - If email OTP is enabled (default, `atw.admin.security.otp-enabled=true`): generates a \
                    one-time code, emails it, and responds with `otpRequired=true` + a `challengeId` - the \
                    caller **must** then call Verify OTP; login is never complete after this call alone.
                    - If OTP is disabled for the environment: responds `otpRequired=false` with `tokens` \
                    populated directly (identical shape to Verify OTP's response).

                    **No account-level lockout, by design:** repeated wrong passwords never lock the \
                    account - brute-force defense comes from the per-IP rate limit below plus the \
                    mandatory OTP step, not a failed-attempt counter (a global per-account lock would \
                    let anyone who merely knows an admin's email keep that admin locked out indefinitely).

                    **Rate limited:** 10 requests/60 seconds per client IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Password accepted. Either an OTP challenge, or (only if OTP is disabled) tokens directly.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = {
                                    @ExampleObject(name = "otpRequired", summary = "Default: OTP challenge issued", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "otpRequired": true,
                                                "challengeId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                                "tokens": null,
                                                "testOtp": "482913"
                                              },
                                              "error": null
                                            }
                                            """),
                                    @ExampleObject(name = "otpDisabled", summary = "atw.admin.security.otp-enabled=false", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "otpRequired": false,
                                                "challengeId": null,
                                                "tokens": {
                                                  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ZDJm...",
                                                  "tokenType": "Bearer",
                                                  "expiresIn": 900,
                                                  "refreshToken": "7f3a9c2e8b1d4f6a0c5e9b2d7a1f4c8e6b3d0a9f2c5e8b1d4f7a0c3e6b9d2f5a",
                                                  "admin": {
                                                    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                                    "username": "superadmin",
                                                    "email": "superadmin@anytimework.in",
                                                    "fullName": "Platform Super Admin",
                                                    "superAdmin": true,
                                                    "roles": [],
                                                    "authorities": ["SUPER_ADMIN"],
                                                    "mustChangePassword": false
                                                  }
                                                },
                                                "testOtp": null
                                              },
                                              "error": null
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unknown username/email, wrong password, or a disabled account.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "UNAUTHORIZED",
                                        "message": "Invalid credentials",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:15:30Z",
                                        "traceId": "9f1c2e3a4b5d6e7f"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many login attempts from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(request.usernameOrEmail(), request.password(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(result.otpRequired()
                ? LoginResponse.otp(result.challengeId(), result.testOtp())
                : LoginResponse.authenticated(result.tokens()));
    }

    @Operation(
            summary = "Verify the login OTP (step 2 of 2) and receive tokens",
            description = """
                    Submits the code for the `challengeId` returned by Login or Resend OTP. On success, \
                    consumes the challenge and returns the full \
                    `AuthTokens` payload directly (not wrapped, unlike Login's response).

                    Once the challenge has recorded `otpMaxAttempts` (default 5) wrong guesses, it is \
                    permanently dead - even the *correct* code no longer works. Get a new one via Resend OTP, \
                    or start over at Login.

                    Check `data.admin.mustChangePassword` on success: if true, send the admin straight to a \
                    change-password screen - every endpoint except `/admin/account/me` and \
                    `/admin/account/change-password` rejects with `MUST_CHANGE_PASSWORD` (403) until it's cleared.

                    **Rate limited:** 10 requests/60 seconds per client IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Code accepted - access + refresh tokens issued.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthTokens.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ZDJm...",
                                        "tokenType": "Bearer",
                                        "expiresIn": 900,
                                        "refreshToken": "7f3a9c2e8b1d4f6a0c5e9b2d7a1f4c8e6b3d0a9f2c5e8b1d4f7a0c3e6b9d2f5a",
                                        "admin": {
                                          "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                          "username": "superadmin",
                                          "email": "superadmin@anytimework.in",
                                          "fullName": "Platform Super Admin",
                                          "superAdmin": true,
                                          "roles": [],
                                          "authorities": ["SUPER_ADMIN"],
                                          "mustChangePassword": false
                                        }
                                      },
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Unknown/expired challenge, or the wrong code (the attempt counter is incremented even on this response).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid or expired verification code",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:00Z",
                                        "traceId": "9f1c2e3a4b5d6e7a"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "This challenge already has otpMaxAttempts (default 5) wrong guesses recorded.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "BUSINESS_RULE_VIOLATION",
                                        "message": "Too many invalid attempts. Please request a new code.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:05Z",
                                        "traceId": "9f1c2e3a4b5d6e80"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many verify-otp attempts from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/verify-otp")
    public ApiResponse<AuthTokens> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        AuthTokens tokens = authService.verifyOtp(request.challengeId(), request.otp(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(tokens);
    }

    @Operation(
            summary = "Resend the login OTP",
            description = """
                    Issues a fresh code for the same login attempt and **immediately invalidates** the \
                    previous challenge - the old `challengeId` stops working for Verify OTP the moment this \
                    succeeds, whether or not it had any attempts left.

                    Throttled independently of the per-IP rate limit below: at most one resend every \
                    `otpResendInterval` (default 60s), and at most `otpMaxResends` (default 3) resends for \
                    the whole chain started at Login - the resend count carries forward onto the new challenge.

                    On success returns a **new** `challengeId` (and a fresh `otpTtl`) - use it for the next \
                    Verify OTP call, not the original one.

                    **Rate limited:** 10 requests/60 seconds per client IP (its own bucket, independent of \
                    the resend-specific cooldown/cap above).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "A new OTP challenge was issued.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResendOtpResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "challengeId": "5c7e9f21-8b3d-4a6e-9f12-3d8b7c6e5a40",
                                        "testOtp": "118302"
                                      },
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "challengeId doesn't exist, or is already consumed/expired.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid or expired verification code",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:08Z",
                                        "traceId": "9f1c2e3a4b5d6e82"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "Resent too soon (before otpResendInterval), or otpMaxResends already used for this chain.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "BUSINESS_RULE_VIOLATION",
                                        "message": "Please wait before requesting another code.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:10Z",
                                        "traceId": "9f1c2e3a4b5d6e83"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many resend-otp requests from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/resend-otp")
    public ApiResponse<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest http) {
        ResendOtpResult result = authService.resendOtp(request.challengeId(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(new ResendOtpResponse(result.challengeId(), result.testOtp()));
    }

    @Operation(
            summary = "Exchange a refresh token for a new access + refresh pair",
            description = """
                    Rotates the refresh token: the presented one is revoked and linked to the newly-issued \
                    one, so it can never be used again under any circumstance.

                    **Reuse detection (RFC 6819 §5.2.2.3):** replaying a refresh token that was already \
                    rotated away is treated as a strong theft signal and revokes the **entire** token \
                    family (every descendant of the original login), not just the token presented here - \
                    both the legitimate user and a potential attacker are forced back to a fresh login.

                    **Not rate limited** - unlike most of this tag's endpoints, this is routine traffic \
                    (called every time the ~15-minute access token expires), not abuse-prone.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "A fresh access + refresh token pair.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthTokens.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ZDJm...",
                                        "tokenType": "Bearer",
                                        "expiresIn": 900,
                                        "refreshToken": "a1f4c8e6b3d0a9f2c5e8b1d4f7a0c3e6b9d2f5a7f3a9c2e8b1d4f6a0c5e9b2d",
                                        "admin": {
                                          "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                          "username": "superadmin",
                                          "email": "superadmin@anytimework.in",
                                          "fullName": "Platform Super Admin",
                                          "superAdmin": true,
                                          "roles": [],
                                          "authorities": ["SUPER_ADMIN"],
                                          "mustChangePassword": false
                                        }
                                      },
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Token not found/expired/already revoked, reused after rotation, or the account is now disabled.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = {
                                    @ExampleObject(name = "expiredOrRevoked", summary = "Ordinary expiry/revocation", value = """
                                            {
                                              "success": false,
                                              "data": null,
                                              "error": {
                                                "code": "UNAUTHORIZED",
                                                "message": "Refresh token has expired or been revoked",
                                                "fieldErrors": null,
                                                "timestamp": "2026-10-24T10:16:15Z",
                                                "traceId": "9f1c2e3a4b5d6e84"
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "reuseDetected", summary = "An already-rotated token was replayed", value = """
                                            {
                                              "success": false,
                                              "data": null,
                                              "error": {
                                                "code": "UNAUTHORIZED",
                                                "message": "This session was revoked for your safety. Please log in again.",
                                                "fieldErrors": null,
                                                "timestamp": "2026-10-24T10:16:20Z",
                                                "traceId": "9f1c2e3a4b5d6e85"
                                              }
                                            }
                                            """)
                            }))
    })
    @PostMapping("/refresh")
    public ApiResponse<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        AuthTokens tokens = authService.refresh(request.refreshToken(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(tokens);
    }

    @Operation(
            summary = "Log out (revoke a refresh token)",
            description = """
                    Revokes the given refresh token. **Always returns success** - an unknown or \
                    already-revoked token is treated identically to a freshly-valid one (idempotent); only \
                    audits a `LOGOUT` event when the token was actually found.

                    **Known limitation, by design:** this does not invalidate the access token the caller \
                    was using - JWT access tokens are stateless and not tracked server-side, so it simply \
                    expires naturally (<= 15 minutes). The frontend must also discard the access token \
                    client-side immediately on logout.

                    **Not rate limited** - there is nothing to abuse here (the call always succeeds).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Always returned, whether or not refreshToken matched an active session.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": null,
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "refreshToken was missing/blank.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Request validation failed",
                                        "fieldErrors": [
                                          { "field": "refreshToken", "message": "must not be blank" }
                                        ],
                                        "timestamp": "2026-10-24T10:16:25Z",
                                        "traceId": "9f1c2e3a4b5d6e86"
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest http) {
        authService.logout(request.refreshToken(), HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "Request a password-reset email",
            description = """
                    **Always returns success**, whether or not the email belongs to an admin - a deliberate \
                    anti-enumeration design (contrast with Login, which does distinguish "unknown identifier" \
                    from "wrong password"). Do not treat this response as confirmation the email exists.

                    If the email does match an admin, emails a reset link valid for `passwordResetTtl` \
                    (default 30 minutes). Calling this repeatedly creates multiple simultaneously-valid \
                    reset tokens; an earlier unused link is not invalidated just by requesting a new one - \
                    they're all invalidated together only once one is actually used (Reset Password) or the \
                    password changes another way (Change Password).

                    **Rate limited:** 10 requests/60 seconds per client IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Always returned - does not reveal whether the email exists.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": null,
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "email was missing/blank or not a syntactically valid email address.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Request validation failed",
                                        "fieldErrors": [
                                          { "field": "email", "message": "must be a well-formed email address" }
                                        ],
                                        "timestamp": "2026-10-24T10:16:30Z",
                                        "traceId": "9f1c2e3a4b5d6e87"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many forgot-password requests from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        authService.forgotPassword(request.email(), HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "Set a new password from a forgot-password reset link",
            description = """
                    Consumes the opaque `token` from the emailed reset link. Four different failure \
                    conditions (unknown token hash, wrong token type, already-used, or expired) all collapse \
                    to the **same** generic message below, by design - so a client can't use the error to \
                    probe whether a token value was ever issued, merely expired, or already used.

                    **On success:** bumps the admin's security version (every access token issued before \
                    this moment stops working immediately, regardless of remaining TTL), revokes every \
                    refresh token for that admin (logs out all devices/sessions), marks this reset token \
                    used, invalidates every *other* outstanding reset token and any pending login OTP, and \
                    emails a "your password was changed" notification.

                    **Rate limited:** 10 requests/60 seconds per client IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Password reset; all of that admin's sessions are now revoked.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": null,
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Unknown token, wrong token type (e.g. an invite token), already used, expired, or newPassword outside 8-100 chars.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid or expired reset link",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:35Z",
                                        "traceId": "9f1c2e3a4b5d6e88"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many reset-password requests from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        authService.resetPassword(request.token(), request.newPassword(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @Operation(
            summary = "Accept a staff invite (set first password)",
            description = """
                    Completes a staff invite issued by `AdminStaffService.create`: sets that brand-new \
                    account's *first* real password (it was created with a random, never-disclosed one, so \
                    it cannot be used to log in until this runs). Uses a different token kind (`INVITE`) \
                    than Reset Password's (`PASSWORD_RESET`) - the two are deliberately separate code paths.

                    Same collapsed-generic-message pattern as Reset Password, for the same anti-probing \
                    reason: unknown token, wrong token type, already-used, and expired (default \
                    `inviteTtl` is 3 days) all return the identical message below.

                    **On success:** also clears `mustChangePassword` (the new staff member can log in \
                    immediately afterward, no forced-change step) and bumps the security version. Unlike \
                    Reset Password, this does **not** revoke refresh tokens (there are none yet - the \
                    account has never logged in) and does **not** send a "password changed" email (there's \
                    no prior password to notify about).

                    **Rate limited:** 10 requests/60 seconds per client IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Invite accepted; the account can now log in with this password.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": null,
                                      "error": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Unknown token, wrong token type (e.g. a reset token), already used, expired, or password outside 8-100 chars.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid or expired invite link",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:16:40Z",
                                        "traceId": "9f1c2e3a4b5d6e89"
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many accept-invite requests from this client IP in the current window.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = RATE_LIMITED_EXAMPLE)))
    })
    @PostMapping("/accept-invite")
    public ApiResponse<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request, HttpServletRequest http) {
        authService.acceptInvite(request.token(), request.password(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
