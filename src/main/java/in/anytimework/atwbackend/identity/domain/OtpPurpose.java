package in.anytimework.atwbackend.identity.domain;

/** Why an OTP was issued. LOGIN (2FA) today; extendable (e.g. SENSITIVE_ACTION) later. */
public enum OtpPurpose {
    LOGIN
}
