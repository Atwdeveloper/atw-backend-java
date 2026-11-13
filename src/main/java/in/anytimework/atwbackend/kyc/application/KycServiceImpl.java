package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.kyc.application.dto.KycProfileDto;
import in.anytimework.atwbackend.kyc.domain.AadhaarVerificationStatus;
import in.anytimework.atwbackend.kyc.domain.WorkerAadhaarVerification;
import in.anytimework.atwbackend.kyc.domain.WorkerAadhaarVerificationRepository;
import in.anytimework.atwbackend.kyc.domain.WorkerKycProfile;
import in.anytimework.atwbackend.kyc.domain.WorkerKycProfileRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class KycServiceImpl implements KycService {

    private final WorkerKycProfileRepository profiles;
    private final WorkerAadhaarVerificationRepository verifications;
    private final IdentityNumberHasher identityNumberHasher;
    private final PermissionChecker permissionChecker;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    KycServiceImpl(WorkerKycProfileRepository profiles, WorkerAadhaarVerificationRepository verifications,
                   IdentityNumberHasher identityNumberHasher, PermissionChecker permissionChecker,
                   ApplicationEventPublisher events, Clock clock) {
        this.profiles = profiles;
        this.verifications = verifications;
        this.identityNumberHasher = identityNumberHasher;
        this.permissionChecker = permissionChecker;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public KycProfileDto getProfile(UUID workerId) {
        return profiles.findByWorkerId(workerId).map(this::toDto)
                .orElse(new KycProfileDto(workerId, null, null, KycStatus.NOT_STARTED, null, null));
    }

    @Override
    @Transactional
    public KycProfileDto submitAadhaar(UUID workerId, String aadhaarNumber) {
        String hash = identityNumberHasher.hash(aadhaarNumber);
        if (profiles.existsByAadhaarNumberHashAndWorkerIdNot(hash, workerId)) {
            throw new ConflictException("This Aadhaar number is already registered to another worker.");
        }
        WorkerKycProfile profile = getOrCreateProfile(workerId);
        profile.updateAadhaar(aadhaarNumber, hash);
        return toDto(profiles.save(profile));
    }

    /**
     * Duplicate-PAN check added 2026-11 (an audit finding: two workers could previously be given the
     * same PAN with no error) - same hash-based mechanism as {@link #submitAadhaar}, see
     * {@link IdentityNumberHasher}'s Javadoc. Deliberately no PAN *format* check here - see
     * {@code SubmitPanRequest}'s Javadoc for why that's a separate, still-open question rather than
     * bundled into this fix.
     */
    @Override
    @Transactional
    public KycProfileDto submitPan(UUID workerId, String panNumber) {
        String hash = identityNumberHasher.hash(panNumber);
        if (profiles.existsByPanNumberHashAndWorkerIdNot(hash, workerId)) {
            throw new ConflictException("This PAN is already registered to another worker.");
        }
        WorkerKycProfile profile = getOrCreateProfile(workerId);
        profile.updatePan(panNumber, hash);
        return toDto(profiles.save(profile));
    }

    /**
     * No pessimistic lock here (unlike the old per-document status recompute this replaced - see
     * {@code WorkerKycProfileRepository.findByIdForUpdate}'s Javadoc for that history): unlike that
     * bug, {@link WorkerAadhaarVerification#markConsumed} always writes a genuinely new, different
     * value ({@code consumedByWorkerId}), so Hibernate's dirty-checking always attempts an UPDATE and
     * the inherited {@code @Version} column reliably catches a genuine double-consumption race (two
     * concurrent worker-creation requests racing to consume the same verification) with a clean 409 -
     * see {@code GlobalExceptionHandler.handleOptimisticLockingFailure}.
     */
    @Override
    @Transactional
    public KycVerificationCompletionResult completeFromVerification(UUID workerId, UUID verificationId) {
        WorkerAadhaarVerification verification = verifications.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Aadhaar verification not found"));
        if (verification.getStatus() != AadhaarVerificationStatus.VERIFIED) {
            throw new BusinessRuleException("This Aadhaar verification has not succeeded and cannot be used.");
        }
        if (verification.isConsumed()) {
            throw new ConflictException("This Aadhaar verification has already been used to create a worker.");
        }
        if (profiles.existsByAadhaarNumberHashAndWorkerIdNot(verification.getAadhaarNumberHash(), workerId)) {
            throw new ConflictException("This Aadhaar number is already registered to another worker.");
        }

        WorkerKycProfile profile = getOrCreateProfile(workerId);
        profile.markVerifiedViaApi(verification.getAadhaarNumber(), verification.getAadhaarNumberHash(),
                verification.getProviderReferenceId(), verification.getVerifiedAt());
        profile = profiles.save(profile);

        verification.markConsumed(clock.instant(), workerId);
        verifications.save(verification);

        events.publishEvent(new KycVerifiedEvent(workerId));

        return new KycVerificationCompletionResult(toDto(profile), verification.getVerifiedPhotoDocumentId());
    }

    @Override
    @Transactional(readOnly = true)
    public KycStatus getStatus(UUID workerId) {
        return profiles.findByWorkerId(workerId).map(WorkerKycProfile::getOverallStatus).orElse(KycStatus.NOT_STARTED);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findWorkerIdsByStatus(Set<KycStatus> statuses) {
        return profiles.findWorkerIdsByOverallStatus(statuses);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, KycStatus> getStatuses(Set<UUID> workerIds) {
        if (workerIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findByWorkerIdIn(workerIds).stream()
                .collect(Collectors.toMap(WorkerKycProfile::getWorkerId, WorkerKycProfile::getOverallStatus));
    }

    private WorkerKycProfile getOrCreateProfile(UUID workerId) {
        return profiles.findByWorkerId(workerId).orElseGet(() -> profiles.save(new WorkerKycProfile(workerId)));
    }

    private KycProfileDto toDto(WorkerKycProfile profile) {
        boolean unmasked = permissionChecker.has("kyc.view_sensitive");
        return new KycProfileDto(
                profile.getWorkerId(),
                mask(profile.getAadhaarNumber(), unmasked),
                mask(profile.getPanNumber(), unmasked),
                profile.getOverallStatus(),
                profile.getVerifiedAt(),
                profile.getVerificationRef());
    }

    private static String mask(String value, boolean unmasked) {
        return unmasked ? value : PiiMasking.maskTail(value);
    }
}
