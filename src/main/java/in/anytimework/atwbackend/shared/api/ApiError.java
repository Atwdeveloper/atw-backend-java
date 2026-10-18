package in.anytimework.atwbackend.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Machine-readable error detail carried inside {@link ApiResponse}.
 *
 * @param code        stable, UPPER_SNAKE_CASE error code clients can switch on
 * @param message     human-readable description (safe to display)
 * @param fieldErrors per-field validation failures, when applicable (otherwise null)
 * @param timestamp   when the error was produced (UTC)
 * @param traceId     correlation id (echoes the {@code X-Correlation-Id} header) for support
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp,
        String traceId
) {
    /** A single field-level validation failure. */
    public record FieldError(String field, String message) {}
}
