package in.anytimework.atwbackend.shared.exception;

import org.springframework.http.HttpStatus;

/** Thrown when input is syntactically valid but violates a business rule. Maps to HTTP 422. */
public class BusinessRuleException extends ApplicationException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", message);
    }
}
