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
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public admin authentication endpoints (no token required). */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(request.usernameOrEmail(), request.password(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(result.otpRequired()
                ? LoginResponse.otp(result.challengeId(), result.testOtp())
                : LoginResponse.authenticated(result.tokens()));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<AuthTokens> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest http) {
        AuthTokens tokens = authService.verifyOtp(request.challengeId(), request.otp(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/resend-otp")
    public ApiResponse<ResendOtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request, HttpServletRequest http) {
        ResendOtpResult result = authService.resendOtp(request.challengeId(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(new ResendOtpResponse(result.challengeId(), result.testOtp()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        AuthTokens tokens = authService.refresh(request.refreshToken(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest http) {
        authService.logout(request.refreshToken(), HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        authService.forgotPassword(request.email(), HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        authService.resetPassword(request.token(), request.newPassword(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }

    /** Completes a staff invite (see {@code AdminStaffService.create}): sets the account's first real password. */
    @PostMapping("/accept-invite")
    public ApiResponse<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest request, HttpServletRequest http) {
        authService.acceptInvite(request.token(), request.password(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
