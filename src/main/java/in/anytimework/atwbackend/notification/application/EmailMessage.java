package in.anytimework.atwbackend.notification.application;

/** A fully-rendered email ready to hand to an {@link EmailSender}. */
public record EmailMessage(String to, String subject, String htmlBody) {
}
