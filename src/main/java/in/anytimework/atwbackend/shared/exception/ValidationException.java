package in.anytimework.atwbackend.shared.exception;

import org.springframework.http.HttpStatus;

/** Thrown for programmatic (non-annotation) input validation failures. Maps to HTTP 400. */
public class ValidationException extends ApplicationException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
