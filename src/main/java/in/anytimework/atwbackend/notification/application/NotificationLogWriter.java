package in.anytimework.atwbackend.notification.application;

import in.anytimework.atwbackend.notification.domain.NotificationLog;
import in.anytimework.atwbackend.notification.domain.NotificationLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the send-attempt audit record in its own transaction ({@code REQUIRES_NEW}), so a FAILED
 * entry is persisted even when the surrounding send is rolled back / rethrown.
 */
@Component
class NotificationLogWriter {

    private final NotificationLogRepository repository;

    NotificationLogWriter(NotificationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSent(String recipient, String templateCode, String subject, String providerMessageId) {
        repository.save(NotificationLog.sent(recipient, templateCode, subject, providerMessageId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailed(String recipient, String templateCode, String subject, String errorDetail) {
        repository.save(NotificationLog.failed(recipient, templateCode, subject, errorDetail));
    }
}
