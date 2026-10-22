package in.anytimework.atwbackend.shared.exception;

import org.springframework.http.HttpStatus;

/** Thrown when authentication fails (bad credentials, disabled/locked account). Maps to HTTP 401. */
public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
