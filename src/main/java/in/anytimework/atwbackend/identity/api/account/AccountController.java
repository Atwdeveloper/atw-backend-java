package in.anytimework.atwbackend.identity.api.account;

import in.anytimework.atwbackend.identity.api.HttpRequests;
import in.anytimework.atwbackend.identity.api.account.dto.ChangePasswordRequest;
import in.anytimework.atwbackend.identity.application.AdminAccountService;
import in.anytimework.atwbackend.identity.application.dto.AdminProfile;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated self-service for the signed-in admin (any authenticated admin; no permission needed). */
@RestController
@RequestMapping("/api/admin/account")
public class AccountController {

    private final AdminAccountService accountService;
    private final CurrentAdmin currentAdmin;

    public AccountController(AdminAccountService accountService, CurrentAdmin currentAdmin) {
        this.accountService = accountService;
        this.currentAdmin = currentAdmin;
    }

    @GetMapping("/me")
    public ApiResponse<AdminProfile> me() {
        return ApiResponse.ok(accountService.profile(currentAdmin.requireId()));
    }

    @GetMapping("/my-permissions")
    public ApiResponse<List<String>> myPermissions() {
        return ApiResponse.ok(accountService.myPermissions(currentAdmin.requireId()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        accountService.changePassword(currentAdmin.requireId(), request.currentPassword(), request.newPassword(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
