package in.anytimework.atwbackend.notification.application;

import in.anytimework.atwbackend.notification.domain.EmailTemplate;
import in.anytimework.atwbackend.notification.domain.EmailTemplateRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
class NotificationServiceImpl implements NotificationService {

    private static final String DEFAULT_LOCALE = "en";

    private final EmailTemplateRepository templates;
    private final TemplateRenderer renderer;
    private final EmailSender emailSender;
    private final NotificationLogWriter logWriter;
    private final ApplicationEventPublisher events;

    NotificationServiceImpl(EmailTemplateRepository templates, TemplateRenderer renderer,
                            EmailSender emailSender, NotificationLogWriter logWriter,
                            ApplicationEventPublisher events) {
        this.templates = templates;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.logWriter = logWriter;
        this.events = events;
    }

    @Override
    public void send(SendEmailCommand command) {
        String locale = command.locale() != null ? command.locale() : DEFAULT_LOCALE;
        EmailTemplate template = templates.findByCodeAndLocaleAndActiveTrue(command.templateCode(), locale)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active email template not found: " + command.templateCode() + " (" + locale + ")"));

        Map<String, Object> variables = command.variables() != null ? command.variables() : Map.of();
        RenderedEmail rendered = renderer.render(
                template.getSubject(), template.getBodyHtml(), variables, Locale.forLanguageTag(locale));

        // The log is written in its own transaction (see NotificationLogWriter), so a FAILED record
        // survives even though we rethrow to signal the failure to the caller.
        try {
            String providerMessageId = emailSender.send(
                    new EmailMessage(command.toEmail(), rendered.subject(), rendered.htmlBody()));
            logWriter.logSent(command.toEmail(), command.templateCode(), rendered.subject(), providerMessageId);
        } catch (RuntimeException ex) {
            logWriter.logFailed(command.toEmail(), command.templateCode(), rendered.subject(), ex.getMessage());
            throw new BusinessRuleException(
                    "Failed to send email '" + command.templateCode() + "' to " + command.toEmail());
        }
    }

    @Override
    @Transactional
    public void enqueue(SendEmailCommand command) {
        // Published inside this transaction, so the outbox persists it atomically with any other work.
        events.publishEvent(new EmailRequestedEvent(command));
    }
}
