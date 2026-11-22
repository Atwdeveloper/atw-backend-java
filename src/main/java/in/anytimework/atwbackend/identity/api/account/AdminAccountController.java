package in.anytimework.atwbackend.identity.api.account;

import in.anytimework.atwbackend.identity.api.account.dto.ChangePasswordRequest;
import in.anytimework.atwbackend.identity.application.AdminAccountService;
import in.anytimework.atwbackend.identity.application.dto.AdminProfile;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping(ApiVersions.V1 + "/admin/account")
@Tag(name = "Admin Account", description = "Authenticated self-service for the signed-in admin: "
        + "who am I, my effective permissions, and changing my own password. Every endpoint here "
        + "requires a valid bearer access token but NO specific permission - every action targets "
        + "the caller's own account, so there is no {id} path parameter anywhere in this tag. "
        + "'me' and 'change-password' are also the only two endpoints reachable while the token's "
        + "mustChangePassword flag is set (every other endpoint in the API rejects with "
        + "MUST_CHANGE_PASSWORD, 403, until it's cleared).")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountController {

    private final AdminAccountService accountService;
    private final CurrentAdmin currentAdmin;

    public AdminAccountController(AdminAccountService accountService, CurrentAdmin currentAdmin) {
        this.accountService = accountService;
        this.currentAdmin = currentAdmin;
    }

    @Operation(
            summary = "Get my own admin profile",
            description = """
                    Returns the signed-in admin's profile: identity, role names, and effective permission \
                    codes. `authorities` is `["SUPER_ADMIN"]` for a super-admin, otherwise the flattened list \
                    of permission codes from their assigned roles.

                    The frontend should check `mustChangePassword` right after login/verify-otp and, if \
                    true, redirect straight to a change-password screen - this endpoint and Change Password \
                    are the only two reachable until it's cleared; every other endpoint rejects with \
                    `MUST_CHANGE_PASSWORD` (403).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "The caller's own profile.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminProfile.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                        "username": "superadmin",
                                        "email": "superadmin@anytimework.in",
                                        "fullName": "Platform Super Admin",
                                        "superAdmin": true,
                                        "roles": [],
                                        "authorities": ["SUPER_ADMIN"],
                                        "mustChangePassword": false
                                      },
                                      "error": null
                                    }
                                    """)))
    })
    @GetMapping("/me")
    public ApiResponse<AdminProfile> me() {
        return ApiResponse.ok(accountService.profile(currentAdmin.requireId()));
    }

    @Operation(
            summary = "Get my effective permission codes",
            description = """
                    Returns the signed-in admin's effective permission codes as a flat array, e.g. \
                    `["admin_staff.view", "admin_roles.view", ...]`.

                    A super-admin gets back `["*"]` - a wildcard meaning "every permission", not a literal \
                    code to look up in the permission catalog. The frontend must special-case `"*"` as \
                    all-access rather than trying to match it against known codes.

                    This is a convenience for driving the UI (show/hide buttons, menu items, etc.) only - \
                    **the server still enforces every permission independently** on each protected endpoint, \
                    so this response is never itself a security boundary.

                    **Blocked while `mustChangePassword` is set:** unlike `me` and `change-password`, this \
                    endpoint is not in the exception list and rejects with `MUST_CHANGE_PASSWORD` (403) \
                    until the forced password change is completed.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "The caller's effective permission codes (or [\"*\"] for a super-admin).",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(type = "string", example = "admin_staff.view")),
                            examples = {
                                    @ExampleObject(name = "regularAdmin", value = """
                                            {
                                              "success": true,
                                              "data": ["admin_staff.view", "admin_roles.view", "companies.edit"],
                                              "error": null
                                            }
                                            """),
                                    @ExampleObject(name = "superAdmin", summary = "Super-admin wildcard", value = """
                                            {
                                              "success": true,
                                              "data": ["*"],
                                              "error": null
                                            }
                                            """)
                            }))
    })
    @GetMapping("/my-permissions")
    public ApiResponse<List<String>> myPermissions() {
        return ApiResponse.ok(accountService.myPermissions(currentAdmin.requireId()));
    }

    @Operation(
            summary = "Change my own password",
            description = """
                    Sets a new password for the signed-in admin and clears `mustChangePassword` - this is \
                    how a bootstrap or invited account's forced-change flag gets cleared.

                    **On success:** bumps the admin's security version, **revokes every refresh token for \
                    every device/session** (forcing a fresh login everywhere, including this one), \
                    invalidates any outstanding forgot-password reset link and pending login OTP, and \
                    emails a "password changed" notification.

                    **Important:** the access token used to make *this* request also stops working \
                    immediately afterward (its baked-in security version is now stale) - the frontend must \
                    log the admin back in, or silently re-authenticate, right after a successful call. Do \
                    not assume the current session continues.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Password changed; every refresh token for this admin is now revoked.",
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
                    description = "currentPassword didn't match the account's actual password, or newPassword was outside 8-100 characters.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Current password is incorrect",
                                        "fieldErrors": null,
                                        "timestamp": "2026-10-24T10:17:00Z",
                                        "traceId": "9f1c2e3a4b5d6e90"
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        accountService.changePassword(currentAdmin.requireId(), request.currentPassword(), request.newPassword(),
                HttpRequests.clientIp(http), HttpRequests.userAgent(http));
        return ApiResponse.ok();
    }
}
