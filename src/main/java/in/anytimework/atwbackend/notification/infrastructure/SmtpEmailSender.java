package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.EmailMessage;
import in.anytimework.atwbackend.notification.application.EmailSender;
import in.anytimework.atwbackend.notification.application.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * SMTP email transport (a real mailbox, e.g. the company's own hosted domain mail). Active when
 * {@code atw.mail.provider=smtp}. Connection details (host/port/credentials/encryption) come from
 * the standard {@code spring.mail.*} properties so Spring Boot's own mail auto-configuration is
 * reused rather than reimplemented. This is the only class that knows about JavaMail.
 */
@Component
@ConditionalOnProperty(name = "atw.mail.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.fromAddress();
        this.fromName = properties.fromName();
    }

    @Override
    public String send(EmailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Failed to prepare SMTP email", e);
        }
        mailSender.send(mimeMessage);
        return extractMessageId(mimeMessage);
    }

    private String extractMessageId(MimeMessage mimeMessage) {
        try {
            String[] headers = mimeMessage.getHeader("Message-ID");
            return headers != null && headers.length > 0 ? headers[0] : "smtp-" + UUID.randomUUID();
        } catch (MessagingException e) {
            return "smtp-" + UUID.randomUUID();
        }
    }
}
