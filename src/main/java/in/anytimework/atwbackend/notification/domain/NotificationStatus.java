package in.anytimework.atwbackend.notification.domain;

/** Outcome of an email send attempt, recorded on {@link NotificationLog}. */
public enum NotificationStatus {
    SENT,
    FAILED
}
