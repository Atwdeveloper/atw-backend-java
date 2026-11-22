package in.anytimework.atwbackend.notification;

import in.anytimework.atwbackend.notification.application.SmsMessage;
import in.anytimework.atwbackend.notification.application.SmsSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test double that captures outgoing SMS messages instead of sending them, so a test can read their content (e.g. an OTP). */
public class CapturingSmsSender implements SmsSender {

    private final List<SmsMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public String send(SmsMessage message) {
        sent.add(message);
        return "test-" + message.toMobileNumber();
    }

    public SmsMessage last() {
        return sent.get(sent.size() - 1);
    }
}
