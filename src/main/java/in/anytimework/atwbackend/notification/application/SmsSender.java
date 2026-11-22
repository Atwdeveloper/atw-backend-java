package in.anytimework.atwbackend.notification.application;

/**
 * Port for sending an SMS. The concrete transport (log in dev, a real gateway later) lives behind
 * this interface and is chosen by configuration ({@code atw.sms.provider}) - the same
 * vendor-independence boundary {@link EmailSender} already provides for email.
 */
public interface SmsSender {

    /** Send the message and return the provider's message id (or a synthetic id), for traceability. */
    String send(SmsMessage message);
}
