package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.EmailMessage;
import in.anytimework.atwbackend.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development email transport: writes the message to the log instead of sending it.
 * Active by default ({@code atw.mail.provider=log}).
 */
@Component
@ConditionalOnProperty(name = "atw.mail.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public String send(EmailMessage message) {
        log.info("[DEV EMAIL] to='{}' subject='{}'\n{}", message.to(), message.subject(), message.htmlBody());
        return "log-" + UUID.randomUUID();
    }
}
