package in.anytimework.atwbackend.shared.security;

import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;

/**
 * Writes an {@link ApiResponse} error body straight to the servlet response.
 *
 * <p>Used by the security entry point, access-denied handler, and other filters that run inside
 * the filter chain — before {@code @RestControllerAdvice} can apply — so they must render
 * the envelope themselves to keep error responses consistent. Public so any filter in the
 * security chain (e.g. {@code AdminSessionPolicyFilter}) can reuse it instead of duplicating
 * response-building logic.</p>
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String code,
                      String message, ObjectMapper objectMapper, Clock clock) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError error = new ApiError(code, message, null, clock.instant(), MDC.get("traceId"));
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
