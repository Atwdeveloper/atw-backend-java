package in.anytimework.atwbackend.notification.application;

/** Result of rendering a template: the final subject line and HTML body. */
public record RenderedEmail(String subject, String htmlBody) {
}
