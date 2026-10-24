package in.anytimework.atwbackend.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all expected, business-level failures.
 *
 * <p>Carries the HTTP status and a stable error code so {@link GlobalExceptionHandler}
 * can translate any subclass into a consistent {@code ApiResponse} without special-casing each one.</p>
 */
public abstract class ApplicationException extends RuntimeException {

    private final transient HttpStatus status;
    private final String code;

    protected ApplicationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
