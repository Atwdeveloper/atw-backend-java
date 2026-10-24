package in.anytimework.atwbackend.shared.security;

import in.anytimework.atwbackend.shared.api.ApiVersions;

import java.util.Set;

/**
 * Single source of truth for the admin-auth path sets that {@link SecurityConfig},
 * {@link AdminSessionPolicyFilter}, and {@link AuthRateLimitFilter} each need - previously three
 * independently-maintained literal path lists, which meant a new public auth endpoint only had to
 * be added to whichever one made it compile, silently leaving it unprotected by the other two with
 * no compiler error to catch the mismatch. These remain three logically distinct sets (public vs.
 * rate-limited vs. allowed-during-forced-password-change are different questions), but now live in
 * one file so adding an endpoint means deciding its membership in all three at once, in one place.
 */
public final class AdminAuthPaths {

    public static final String AUTH_PREFIX = ApiVersions.V1 + "/admin/auth/";

    /** Public, abuse-prone endpoints capped by {@link AuthRateLimitFilter}. Deliberately excludes logout (not abuse-prone). */
    public static final Set<String> RATE_LIMITED = Set.of(
            AUTH_PREFIX + "login",
            AUTH_PREFIX + "verify-otp",
            AUTH_PREFIX + "resend-otp",
            AUTH_PREFIX + "forgot-password",
            AUTH_PREFIX + "reset-password",
            AUTH_PREFIX + "accept-invite"
    );

    /** The only two endpoints reachable while an admin's mustChangePassword flag is set. */
    public static final Set<String> ALLOWED_WHILE_MUST_CHANGE_PASSWORD = Set.of(
            ApiVersions.V1 + "/admin/account/change-password",
            ApiVersions.V1 + "/admin/account/me"
    );

    private AdminAuthPaths() {
    }
}
