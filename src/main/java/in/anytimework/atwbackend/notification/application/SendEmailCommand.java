package in.anytimework.atwbackend.notification.application;

import java.util.Map;

/**
 * Request to send a templated email.
 *
 * @param templateCode which {@code email_template} to use
 * @param toEmail      recipient address
 * @param variables    values substituted into the template
 * @param locale       template locale (defaults to "en")
 */
public record SendEmailCommand(String templateCode, String toEmail, Map<String, Object> variables, String locale) {

    public SendEmailCommand(String templateCode, String toEmail, Map<String, Object> variables) {
        this(templateCode, toEmail, variables, "en");
    }
}
