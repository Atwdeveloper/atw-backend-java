package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;

/**
 * Port for Aadhaar OTP-based e-KYC verification (send an OTP to the mobile UIDAI has on file for an
 * Aadhaar number; verify it and receive UIDAI-sourced identity details back). Zero vendor-specific
 * concepts appear here - the concrete provider (a licensed KUA/ASA such as Signzy/IDfy/Digio/Surepass)
 * lives entirely behind this interface in {@code kyc.infrastructure} and is chosen by configuration
 * ({@code atw.aadhaar-kyc.provider}), so swapping vendors is one new adapter class and a config
 * change - never a change to {@code WorkerIntakeService} or anything above this port.
 */
public interface AadhaarKycPort {

    /**
     * @return the provider's opaque reference id, to be passed to {@link #verifyOtp}
     * @throws BusinessRuleException if the provider rejects the request (unrecognized Aadhaar number,
     *         cooldown in effect, provider outage) - surfaced to the admin as a clean 422, not a crash
     */
    String sendOtp(String aadhaarNumber);

    /**
     * @throws BusinessRuleException if the OTP is wrong/expired or the provider call otherwise fails
     */
    AadhaarVerifiedDetails verifyOtp(String referenceId, String otp);
}
