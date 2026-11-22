package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.IdentityNumberHasher;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatutoryDetailResponse;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import in.anytimework.atwbackend.worker.domain.WorkerStatutoryDetail;
import in.anytimework.atwbackend.worker.domain.WorkerStatutoryDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A worker's statutory identity numbers (UAN, ESIC). Masking/permission shape mirrors
 * {@code WorkerBankDetailService}: {@code workers.view}/{@code workers.edit} (checked at the
 * controller) gate this sub-resource; the unmasked value is returned only to a caller who also holds
 * {@code kyc.view_sensitive} - reused from the kyc module rather than minted fresh, since UAN/ESIC
 * are the same risk category as Aadhaar/PAN (government identity numbers), unlike a bank account
 * number (see {@code workers.view_bank_details}).
 */
@Service
public class WorkerStatutoryDetailService {

    private final WorkerRepository workers;
    private final WorkerStatutoryDetailRepository statutoryDetails;
    private final PermissionChecker permissionChecker;
    private final IdentityNumberHasher identityNumberHasher;

    public WorkerStatutoryDetailService(WorkerRepository workers, WorkerStatutoryDetailRepository statutoryDetails,
                                        PermissionChecker permissionChecker, IdentityNumberHasher identityNumberHasher) {
        this.workers = workers;
        this.statutoryDetails = statutoryDetails;
        this.permissionChecker = permissionChecker;
        this.identityNumberHasher = identityNumberHasher;
    }

    @Transactional(readOnly = true)
    public WorkerStatutoryDetailResponse get(UUID workerId) {
        requireWorkerExists(workerId);
        return statutoryDetails.findByWorkerId(workerId).map(this::toDto)
                .orElse(new WorkerStatutoryDetailResponse(null, null));
    }

    @Transactional
    public WorkerStatutoryDetailResponse updateUan(UUID workerId, String uanNumber) {
        requireWorkerExists(workerId);
        String hash = identityNumberHasher.hash(uanNumber);
        if (statutoryDetails.existsByUanNumberHashAndWorkerIdNot(hash, workerId)) {
            throw new ConflictException("This UAN is already registered to another worker.");
        }
        WorkerStatutoryDetail detail = getOrCreate(workerId);
        detail.updateUan(uanNumber, hash);
        return toDto(statutoryDetails.save(detail));
    }

    @Transactional
    public WorkerStatutoryDetailResponse updateEsic(UUID workerId, String esicNumber) {
        requireWorkerExists(workerId);
        String hash = identityNumberHasher.hash(esicNumber);
        if (statutoryDetails.existsByEsicNumberHashAndWorkerIdNot(hash, workerId)) {
            throw new ConflictException("This ESIC number is already registered to another worker.");
        }
        WorkerStatutoryDetail detail = getOrCreate(workerId);
        detail.updateEsic(esicNumber, hash);
        return toDto(statutoryDetails.save(detail));
    }

    private WorkerStatutoryDetail getOrCreate(UUID workerId) {
        return statutoryDetails.findByWorkerId(workerId).orElseGet(() -> new WorkerStatutoryDetail(workerId));
    }

    private void requireWorkerExists(UUID workerId) {
        if (!workers.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
    }

    private WorkerStatutoryDetailResponse toDto(WorkerStatutoryDetail detail) {
        boolean unmasked = permissionChecker.has("kyc.view_sensitive");
        String uan = unmasked ? detail.getUanNumber() : PiiMasking.maskTail(detail.getUanNumber());
        String esic = unmasked ? detail.getEsicNumber() : PiiMasking.maskTail(detail.getEsicNumber());
        return new WorkerStatutoryDetailResponse(uan, esic);
    }
}
