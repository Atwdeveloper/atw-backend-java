package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class FaceAuthServiceImpl implements FaceAuthService {

    private static final int EMBEDDING_DIMENSIONS = 128;
    private static final Duration PHOTO_URL_VALIDITY = Duration.ofMinutes(15);

    private final WorkerFaceEnrollmentRepository enrollments;
    private final FaceAuthPort faceAuthPort;
    private final FaceEnrollmentPhotoSupport photoSupport;
    private final DocumentService documentService;
    private final Clock clock;

    FaceAuthServiceImpl(WorkerFaceEnrollmentRepository enrollments, FaceAuthPort faceAuthPort,
                        FaceEnrollmentPhotoSupport photoSupport, DocumentService documentService, Clock clock) {
        this.enrollments = enrollments;
        this.faceAuthPort = faceAuthPort;
        this.photoSupport = photoSupport;
        this.documentService = documentService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FaceEnrollmentDto getEnrollment(UUID workerId) {
        return enrollments.findByWorkerId(workerId).map(this::toDto)
                .orElse(new FaceEnrollmentDto(workerId, false, null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEnrollment(UUID workerId) {
        return enrollments.existsByWorkerId(workerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Instant> getEnrollmentDates(Set<UUID> workerIds) {
        if (workerIds.isEmpty()) {
            return Map.of();
        }
        return enrollments.findByWorkerIdIn(workerIds).stream()
                .collect(Collectors.toMap(WorkerFaceEnrollment::getWorkerId, WorkerFaceEnrollment::getEnrolledAt));
    }

    @Override
    @Transactional
    public FaceEnrollmentDto enroll(UUID workerId, String filename, String contentType, byte[] content,
                                    boolean consentGiven, String consentIp) {
        if (!consentGiven) {
            throw new ValidationException("Worker consent is required before enrolling a face template.");
        }
        photoSupport.validate(contentType, content);

        FaceEmbeddingResult result = faceAuthPort.embed(content, contentType);
        if (result.embedding() == null || result.embedding().length != EMBEDDING_DIMENSIONS) {
            throw new BusinessRuleException("Face-auth service returned an unexpected embedding size.");
        }
        if (!result.quality().acceptable()) {
            throw new BusinessRuleException("Image quality is insufficient for enrollment"
                    + (result.quality().reasons().isEmpty() ? "." : ": " + String.join(", ", result.quality().reasons())));
        }
        if (!result.live()) {
            throw new BusinessRuleException("Liveness check failed - please retake the photo of the worker in person.");
        }

        WorkerFaceEnrollment existing = enrollments.findByWorkerId(workerId).orElse(null);
        UUID previousPhotoId = existing == null ? null : existing.getEnrollPhotoDocumentId();
        UUID photoDocumentId = photoSupport.store(workerId, previousPhotoId, filename, contentType, content);

        Instant now = clock.instant();
        WorkerFaceEnrollment enrollment;
        if (existing != null) {
            existing.reEnroll(result.embedding(), result.quality(), result.livenessConfidence(), photoDocumentId, now, now, consentIp);
            enrollment = existing;
        } else {
            enrollment = new WorkerFaceEnrollment(workerId, result.embedding(), result.quality(),
                    result.livenessConfidence(), photoDocumentId, now, now, consentIp);
        }
        return toDto(enrollments.save(enrollment));
    }

    @Override
    @Transactional
    public void removeEnrollment(UUID workerId) {
        enrollments.findByWorkerId(workerId).ifPresent(e -> {
            photoSupport.remove(e.getEnrollPhotoDocumentId());
            enrollments.delete(e);
        });
    }

    private FaceEnrollmentDto toDto(WorkerFaceEnrollment e) {
        String photoUrl = e.getEnrollPhotoDocumentId() == null ? null
                : documentService.generateDownloadUrl(e.getEnrollPhotoDocumentId(), PHOTO_URL_VALIDITY).toString();
        return new FaceEnrollmentDto(e.getWorkerId(), true, e.getEnrolledAt(), e.getLivenessConfidence(), photoUrl);
    }
}
