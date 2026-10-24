package in.anytimework.atwbackend.shared.security;

import jakarta.servlet.http.HttpServletRequest;

/** Small helpers to extract client metadata for auditing and rate limiting. */
public final class HttpRequests {

    private HttpRequests() {
    }

    /**
     * Best-effort client IP, used for both audit attribution and the per-IP rate limiter - which
     * means it must not be trivially spoofable, or a client can single-handedly defeat the rate
     * limit by sending a different value on every request.
     *
     * <p>{@code X-Forwarded-For} is a client-supplied header: a well-behaved reverse proxy
     * <em>appends</em> the IP it actually observed to whatever was already there, so the entry
     * nearest the server (the last one) is the one a trusted hop actually vouches for - anything
     * earlier in the list is attacker-controlled chaff. This assumes exactly one trusted proxy
     * hop in front of the app (Azure Container Apps' own ingress) with no additional gateway/WAF
     * layer added later; if that topology changes, this needs to trust a different hop count
     * (e.g. the Nth-from-last), not just "the last one".</p>
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
