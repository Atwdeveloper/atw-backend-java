package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerBankDetailRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerBankDetailResponse;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetail;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetailRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A worker's bank/payout details; the account number is encrypted at rest. {@code workers.view}
 * (checked at the controller) only ever sees a masked account number from here; the full decrypted
 * value is returned only to a caller who also holds {@code kyc.view_sensitive} - reusing that
 * permission rather than minting a worker-specific one, since a bank account number is comparably
 * identifying to the Aadhaar/PAN numbers it already gates.
 */
@Service
public class WorkerBankDetailService {

    private final WorkerRepository workers;
    private final WorkerBankDetailRepository bankDetails;
    private final PermissionChecker permissionChecker;

    public WorkerBankDetailService(WorkerRepository workers, WorkerBankDetailRepository bankDetails,
                                   PermissionChecker permissionChecker) {
        this.workers = workers;
        this.bankDetails = bankDetails;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public WorkerBankDetailResponse get(UUID workerId) {
        requireWorkerExists(workerId);
        return bankDetails.findByWorkerId(workerId).map(this::toDto)
                .orElse(new WorkerBankDetailResponse(null, null, null, null, null, false, null));
    }

    @Transactional
    public WorkerBankDetailResponse update(UUID workerId, UpdateWorkerBankDetailRequest request) {
        requireWorkerExists(workerId);
        WorkerBankDetail detail = bankDetails.findByWorkerId(workerId).orElseGet(() -> new WorkerBankDetail(workerId));
        detail.update(request.accountHolderName(), request.bankName(), request.accountNumber(),
                request.ifscCode(), request.branchName());
        detail = bankDetails.save(detail);
        return toDto(detail);
    }

    private void requireWorkerExists(UUID workerId) {
        if (!workers.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
    }

    private WorkerBankDetailResponse toDto(WorkerBankDetail detail) {
        boolean unmasked = permissionChecker.has("kyc.view_sensitive");
        String accountNumber = unmasked ? detail.getAccountNumber() : PiiMasking.maskTail(detail.getAccountNumber());
        return new WorkerBankDetailResponse(detail.getAccountHolderName(), detail.getBankName(), accountNumber,
                detail.getIfscCode(), detail.getBranchName(), detail.isVerified(), detail.getVerifiedAt());
    }
}
