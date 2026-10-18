package in.anytimework.atwbackend.shared.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an operation conflicts with current state (e.g. a duplicate). Maps to HTTP 409. */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
