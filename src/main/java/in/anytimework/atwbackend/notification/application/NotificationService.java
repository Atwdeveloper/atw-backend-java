package in.anytimework.atwbackend.notification.application;

/**
 * Public API of the notification module. Other modules either call these methods directly, or
 * (preferably) publish their own domain events that this module listens to.
 */
public interface NotificationService {

    /** Render and send immediately, recording the outcome. Throws if sending fails. */
    void send(SendEmailCommand command);

    /**
     * Hand the send off to the transactional outbox: it is persisted in the caller's transaction and
     * delivered asynchronously, with automatic retry on failure. Returns immediately.
     */
    void enqueue(SendEmailCommand command);

    /**
     * Send an SMS immediately, synchronously - unlike email, an OTP SMS's caller needs to know
     * right away whether it actually went out before telling an admin "code sent, check the phone".
     * Plain text, no DB-template lookup: there is exactly one SMS message type today (OTP codes);
     * template indirection is worth adding if/when a second one appears. Throws if sending fails.
     */
    void sendSms(String toMobileNumber, String body);
}
