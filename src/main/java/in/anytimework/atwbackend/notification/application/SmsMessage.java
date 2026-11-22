package in.anytimework.atwbackend.notification.application;

/** A plain-text SMS ready to hand to an {@link SmsSender}. No template indirection yet - see SmsSender's Javadoc. */
public record SmsMessage(String toMobileNumber, String body) {
}
