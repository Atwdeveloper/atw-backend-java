package in.anytimework.atwbackend.notification;

import in.anytimework.atwbackend.notification.application.EmailMessage;
import in.anytimework.atwbackend.notification.application.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double that captures outgoing emails instead of sending them, so a test can read their content (e.g. an OTP or link). */
public class CapturingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public String send(EmailMessage message) {
        sent.add(message);
        return "test-" + message.to();
    }

    public EmailMessage last() {
        return sent.get(sent.size() - 1);
    }
}
