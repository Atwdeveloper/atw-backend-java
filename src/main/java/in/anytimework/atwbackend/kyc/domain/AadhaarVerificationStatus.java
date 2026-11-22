package in.anytimework.atwbackend.kyc.domain;

/** Lifecycle of one Aadhaar e-KYC verification attempt. Expiry is computed from {@code expiresAt}, not a stored state. */
public enum AadhaarVerificationStatus {
    OTP_SENT,
    VERIFIED,
    FAILED
}
