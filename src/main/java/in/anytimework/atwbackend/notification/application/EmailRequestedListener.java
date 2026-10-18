package in.anytimework.atwbackend.notification.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Handles {@link EmailRequestedEvent} from the transactional outbox.
 *
 * <p>{@code @ApplicationModuleListener} = run after the publishing transaction commits, asynchronously,
 * in a fresh transaction, tracked by the event publication registry. If the send throws, the event
 * stays incomplete and is retried (e.g. on restart), giving at-least-once delivery.</p>
 */
@Component
class EmailRequestedListener {

    private final NotificationService notificationService;

    EmailRequestedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ApplicationModuleListener
    void on(EmailRequestedEvent event) {
        notificationService.send(event.command());
    }
}
