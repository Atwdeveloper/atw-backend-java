package in.anytimework.atwbackend.worker.application;

/**
 * Raw outcome of a bank-account-verification call - deliberately does not decide whether this counts
 * as "verified" for {@link in.anytimework.atwbackend.worker.domain.WorkerBankDetail}; that policy
 * (does {@code nameAtBank} sufficiently match the worker's declared holder name) belongs to
 * {@code WorkerBankDetailService.verify}, not to any adapter of {@link BankVerificationPort}.
 *
 * @param exists whether the provider confirmed the account number + IFSC combination is real/active
 * @param nameAtBank the account holder name the bank has on file, as the provider returned it - null
 *                    if the provider doesn't return one, or if {@code exists} is false
 * @param providerReference the provider's own reference/transaction id for this call, kept for audit -
 *                           not secret
 */
public record BankVerificationResult(boolean exists, String nameAtBank, String providerReference) {
}
