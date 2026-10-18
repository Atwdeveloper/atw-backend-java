package in.anytimework.atwbackend.notification.application;

/**
 * Port for sending an email. The concrete transport (log in dev, Azure Communication Services in the
 * cloud) lives behind this interface and is chosen by configuration — the vendor-independence boundary.
 */
public interface EmailSender {

    /**
     * Send the message and return the provider's message id (or a synthetic id), for traceability.
     */
    String send(EmailMessage message);
}
