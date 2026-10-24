package in.anytimework.atwbackend.shared.api;

/**
 * API path version prefixes, centralized so every controller's {@code @RequestMapping} stays
 * consistent and a version bump is a one-place change. Path-segment versioning (not a header or
 * media-type scheme) is deliberate: with exactly one version in use, a literal URL prefix is the
 * simplest correct choice. Spring's content-negotiation-based versioning (see
 * {@code WebMvcConfigurer#configureApiVersioning}) remains available later if two versions ever
 * need to coexist behind the same path.
 */
public final class ApiVersions {

    public static final String V1 = "/api/v1";

    private ApiVersions() {
    }
}
