package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;

/**
 * Port to a GST-registration-verification vendor (confirms a GSTIN is real, active, and reports the
 * legal name/state on file for it). Zero vendor-specific concepts appear here - the concrete
 * provider lives entirely behind this interface in {@code company.infrastructure} and is chosen by
 * configuration ({@code atw.gst-verification.provider}), the same shape as
 * {@code worker.application.BankVerificationPort}/{@code kyc.application.AadhaarKycPort}.
 *
 * <p>Deliberately decoupled from company creation - see {@code CompanyService.create}'s Javadoc:
 * creating a company never depends on this call succeeding, so a vendor outage can never block
 * onboarding. {@code CompanyService.triggerGstVerification} is the only caller.</p>
 */
public interface GstVerificationPort {

    /**
     * @param gstNumber the company's own stored (decrypted) GST number
     * @throws BusinessRuleException if the provider is unreachable, times out, or rejects the request
     *         outright - surfaced to the admin as a clean, retryable error, not a crash. An invalid
     *         GSTIN pattern, a GSTIN the provider could not find, or one that is on file but not
     *         active, is NOT an exception here - it comes back as a normal result with
     *         {@code verified=false}, for the caller to act on (see {@link GstVerificationResult}).
     */
    GstVerificationResult verify(String gstNumber);
}
