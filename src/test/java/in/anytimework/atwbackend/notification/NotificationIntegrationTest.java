package in.anytimework.atwbackend.notification;

import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.notification.domain.NotificationLog;
import in.anytimework.atwbackend.notification.domain.NotificationLogRepository;
import in.anytimework.atwbackend.notification.domain.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end email tests against the real local Postgres (no Docker needed): the WELCOME template is
 * read from the DB, rendered, "sent" via the dev log sender, and the attempt is recorded. The async
 * case also exercises the Modulith outbox (event publication registry).
 */
@SpringBootTest
class NotificationIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository logRepository;

    @Test
    void send_rendersTemplateFromDbAndLogsSuccess() {
        String recipient = "sync-" + UUID.randomUUID() + "@example.com";

        notificationService.send(new SendEmailCommand("WELCOME", recipient, Map.of("name", "Asha")));

        List<NotificationLog> logs = logRepository.findByRecipientOrderByCreatedAtDesc(recipient);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(logs.get(0).getSubject()).isEqualTo("Welcome to Anytime Work, Asha!");
    }

    @Test
    void enqueue_deliversAsynchronouslyThroughOutbox() {
        String recipient = "async-" + UUID.randomUUID() + "@example.com";

        notificationService.enqueue(new SendEmailCommand("WELCOME", recipient, Map.of("name", "Ravi")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(logRepository.findByRecipientOrderByCreatedAtDesc(recipient))
                        .anySatisfy(entry -> assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT)));
    }
}
