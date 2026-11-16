package in.anytimework.atwbackend.worker.infrastructure;

import in.anytimework.atwbackend.worker.application.BankVerificationPort;
import in.anytimework.atwbackend.worker.application.BankVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Development/demo bank-verification adapter: no real vendor account exists yet, so this simulates
 * the provider instead of calling one. Active by default ({@code atw.bank-verification.provider=stub}).
 * Always reports the account as existing with no bank-side name to compare (so the stub never itself
 * triggers a name-mismatch rejection) - deterministic, not random, same convention as
 * {@code StubAadhaarKycAdapter}.
 */
@Component
@ConditionalOnProperty(name = "atw.bank-verification.provider", havingValue = "stub", matchIfMissing = true)
public class StubBankVerificationAdapter implements BankVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubBankVerificationAdapter.class);

    @Override
    public BankVerificationResult verify(String accountNumber, String ifscCode) {
        log.info("[STUB BANK VERIFICATION] Simulating a successful verification for IFSC {}", ifscCode);
        return new BankVerificationResult(true, null, "stub-" + UUID.randomUUID());
    }
}
