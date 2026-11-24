package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import in.anytimework.atwbackend.worker.api.admin.dto.BankAccountCheckResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerBankDetailRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerBankDetailResponse;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetail;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetailRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * A worker's bank/payout details; the account number is encrypted at rest. {@code workers.view}
 * (checked at the controller) only ever sees a masked account number from here; the full decrypted
 * value is returned only to a caller who also holds {@code workers.view_bank_details} - a dedicated
 * unmask permission, independent of {@code kyc.view_sensitive} (which unmasks Aadhaar/PAN/UAN/ESIC
 * instead). Bank account numbers and government identity numbers are different risk categories -
 * financial/payout fraud vs. identity-document exposure - so granting one no longer silently grants
 * the other. Mirrors how {@code admin_staff.view_bank_details}/{@code admin_staff.view_kyc} already
 * split the same two concerns for the identity module's own staff records.
 */
@Service
public class WorkerBankDetailService {

    private final WorkerRepository workers;
    private final WorkerBankDetailRepository bankDetails;
    private final PermissionChecker permissionChecker;
    private final BankVerificationPort bankVerificationPort;
    private final Clock clock;

    public WorkerBankDetailService(WorkerRepository workers, WorkerBankDetailRepository bankDetails,
                                   PermissionChecker permissionChecker, BankVerificationPort bankVerificationPort,
                                   Clock clock) {
        this.workers = workers;
        this.bankDetails = bankDetails;
        this.permissionChecker = permissionChecker;
        this.bankVerificationPort = bankVerificationPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkerBankDetailResponse get(UUID workerId) {
        requireWorkerExists(workerId);
        return bankDetails.findByWorkerId(workerId).map(this::toDto)
                .orElse(new WorkerBankDetailResponse(null, null, null, null, false, null));
    }

    @Transactional
    public WorkerBankDetailResponse update(UUID workerId, UpdateWorkerBankDetailRequest request) {
        requireWorkerExists(workerId);
        WorkerBankDetail detail = bankDetails.findByWorkerId(workerId).orElseGet(() -> new WorkerBankDetail(workerId));
        detail.update(request.accountHolderName(), request.bankName(), request.accountNumber(), request.ifscCode());
        detail = bankDetails.save(detail);
        return toDto(detail);
    }

    /**
     * Calls the configured {@link BankVerificationPort} with the account/IFSC already on file and
     * applies this codebase's match policy (see {@link #check}). Nothing is persisted unless both
     * checks pass - {@code verified}/{@code verifiedAt} are never settable any other way, closing a
     * gap the legacy app had (it let a client set its equivalent flag directly, bypassing any real
     * vendor check).
     */
    @Transactional
    public WorkerBankDetailResponse verify(UUID workerId) {
        requireWorkerExists(workerId);
        WorkerBankDetail detail = bankDetails.findByWorkerId(workerId)
                .orElseThrow(() -> new BusinessRuleException("No bank details on file for this worker yet."));

        BankAccountCheckResponse result = check(detail.getAccountNumber(), detail.getIfscCode(), detail.getAccountHolderName());
        if (!result.exists()) {
            throw new BusinessRuleException(
                    "The bank could not confirm this account - please re-check the account number and IFSC code.");
        }
        if (!result.nameMatched()) {
            throw new ConflictException(
                    "The bank's name on this account does not match the declared account holder name - "
                            + "please correct it and try again.");
        }

        detail.markVerified(clock.instant());
        detail = bankDetails.save(detail);
        return toDto(detail);
    }

    /**
     * Standalone, worker-independent check against the configured {@link BankVerificationPort} -
     * nothing is persisted, and no worker needs to exist yet (useful during intake, before a worker
     * record exists - see {@code AdminWorkerController.checkBankAccount}). Applies this codebase's
     * match policy: the provider must confirm the account exists, and if it returns a name-at-account
     * at all, that name must reasonably match (case/whitespace-insensitive) {@code accountHolderName}.
     * A provider that doesn't return a name (e.g. the default stub) skips the name check entirely
     * rather than blocking on it. Shared by {@link #verify} (which re-runs this against the already-
     * persisted row, then - and only then - sets {@code verified}/{@code verifiedAt}) so the match
     * policy is defined in exactly one place.
     */
    @Transactional(readOnly = true)
    public BankAccountCheckResponse check(String accountNumber, String ifscCode, String accountHolderName) {
        BankVerificationResult result = bankVerificationPort.verify(accountNumber, ifscCode);
        boolean nameMatched = result.exists()
                && (result.nameAtBank() == null || namesMatch(accountHolderName, result.nameAtBank()));
        return new BankAccountCheckResponse(result.exists(), nameMatched, result.nameAtBank());
    }

    private static boolean namesMatch(String declared, String atBank) {
        return normalize(declared).equals(normalize(atBank));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private void requireWorkerExists(UUID workerId) {
        if (!workers.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
    }

    private WorkerBankDetailResponse toDto(WorkerBankDetail detail) {
        boolean unmasked = permissionChecker.has("workers.view_bank_details");
        String accountNumber = unmasked ? detail.getAccountNumber() : PiiMasking.maskTail(detail.getAccountNumber());
        return new WorkerBankDetailResponse(detail.getAccountHolderName(), detail.getBankName(), accountNumber,
                detail.getIfscCode(), detail.isVerified(), detail.getVerifiedAt());
    }
}
