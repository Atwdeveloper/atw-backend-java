package in.anytimework.atwbackend.identity.api;

import jakarta.servlet.http.HttpServletRequest;

/** Small helpers to extract client metadata for auditing. */
public final class HttpRequests {

    private HttpRequests() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
