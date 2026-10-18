/**
 * Cross-cutting technical foundation reused by every business module:
 * API response envelope, error handling, persistence base + auditing,
 * security baseline, and configuration (OpenAPI, clock, correlation id, CORS).
 *
 * <p>Marked {@code OPEN} for Spring Modulith: unlike business modules, its sub-packages
 * are intentionally visible to all other modules. It must never depend on a business module.</p>
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package in.anytimework.atwbackend.shared;
