package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.SmsMessage;
import in.anytimework.atwbackend.notification.application.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development SMS transport: writes the message to the log instead of sending it.
 * Active by default ({@code atw.sms.provider=log}).
 */
@Component
@ConditionalOnProperty(name = "atw.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public String send(SmsMessage message) {
        log.info("[DEV SMS] to='{}'\n{}", message.toMobileNumber(), message.body());
        return "log-" + UUID.randomUUID();
    }
}
