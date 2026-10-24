package in.anytimework.atwbackend.notification.application;

import in.anytimework.atwbackend.notification.domain.EmailTemplate;
import in.anytimework.atwbackend.notification.domain.EmailTemplateRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the email orchestration logic (no Spring, no real email/DB). */
class NotificationServiceImplTest {

    private final EmailTemplateRepository templates = mock(EmailTemplateRepository.class);
    private final TemplateRenderer renderer = mock(TemplateRenderer.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final NotificationLogWriter logWriter = mock(NotificationLogWriter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationServiceImpl service =
            new NotificationServiceImpl(templates, renderer, emailSender, logWriter, events);

    private EmailTemplate welcomeTemplate() {
        return new EmailTemplate("WELCOME", "Hi [(${name})]", "<p>[[${name}]]</p>", "en", true, null);
    }

    @Test
    void send_rendersSendsAndLogsSuccess() {
        when(templates.findByCodeAndLocaleAndActiveTrue("WELCOME", "en"))
                .thenReturn(Optional.of(welcomeTemplate()));
        when(renderer.render(any(), any(), any(), any()))
                .thenReturn(new RenderedEmail("Hi Asha", "<p>Asha</p>"));
        when(emailSender.send(any())).thenReturn("provider-123");

        service.send(new SendEmailCommand("WELCOME", "asha@example.com", Map.of("name", "Asha")));

        verify(emailSender).send(any(EmailMessage.class));
        verify(logWriter).logSent("asha@example.com", "WELCOME", "Hi Asha", "provider-123");
    }

    @Test
    void send_whenSenderFails_logsFailureAndThrows() {
        when(templates.findByCodeAndLocaleAndActiveTrue("WELCOME", "en"))
                .thenReturn(Optional.of(welcomeTemplate()));
        when(renderer.render(any(), any(), any(), any()))
                .thenReturn(new RenderedEmail("Hi Asha", "<p>Asha</p>"));
        when(emailSender.send(any())).thenThrow(new RuntimeException("smtp down"));

        assertThatThrownBy(() -> service.send(
                new SendEmailCommand("WELCOME", "asha@example.com", Map.of("name", "Asha"))))
                .isInstanceOf(BusinessRuleException.class);

        verify(logWriter).logFailed(eq("asha@example.com"), eq("WELCOME"), eq("Hi Asha"), eq("smtp down"));
    }

    @Test
    void send_whenTemplateMissing_throwsNotFound() {
        when(templates.findByCodeAndLocaleAndActiveTrue(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(
                new SendEmailCommand("NOPE", "asha@example.com", Map.of())))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(emailSender, never()).send(any());
    }

    @Test
    void enqueue_publishesOutboxEvent() {
        SendEmailCommand command = new SendEmailCommand("WELCOME", "asha@example.com", Map.of("name", "Asha"));

        service.enqueue(command);

        verify(events).publishEvent(new EmailRequestedEvent(command));
    }
}
