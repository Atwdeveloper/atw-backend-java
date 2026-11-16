package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;

/**
 * Port to a bank-account-verification vendor (a "penny-drop"/account-validation API: confirms an
 * account number + IFSC combination is real and active, and reports the name the bank itself has on
 * file for it). Zero vendor-specific concepts appear here - the concrete provider lives entirely
 * behind this interface in {@code worker.infrastructure} and is chosen by configuration
 * ({@code atw.bank-verification.provider}), the same shape as {@code AadhaarKycPort}/
 * {@code FaceAuthPort}. Deciding whether the bank's name-at-account sufficiently matches the worker's
 * declared holder name is an application-level policy decision (see
 * {@code WorkerBankDetailService.verify}), never this port's concern - it only reports facts.
 */
public interface BankVerificationPort {

    /**
     * @throws BusinessRuleException if the provider is unreachable, times out, or rejects the request
     *         outright - surfaced to the admin as a clean, retryable error, not a crash. An account
     *         the provider confirms does NOT exist/is inactive is NOT an exception here - it comes
     *         back as a normal result with {@code exists=false}, for the caller to act on.
     */
    BankVerificationResult verify(String accountNumber, String ifscCode);
}
