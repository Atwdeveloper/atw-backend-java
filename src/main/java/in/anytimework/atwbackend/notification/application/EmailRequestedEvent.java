package in.anytimework.atwbackend.notification.application;

/**
 * Published when an email should be sent asynchronously. Persisted by Spring Modulith's event
 * publication registry (the transactional outbox), so a send is never lost even if the process
 * crashes before it completes.
 */
public record EmailRequestedEvent(SendEmailCommand command) {
}
