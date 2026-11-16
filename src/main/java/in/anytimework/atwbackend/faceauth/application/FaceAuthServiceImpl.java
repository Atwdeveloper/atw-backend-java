package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationCandidate;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationResult;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class FaceAuthServiceImpl implements FaceAuthService {

    private static final int EMBEDDING_DIMENSIONS = 128;
    private static final int MAX_TOP_MATCHES = 5;
    private static final Duration PHOTO_URL_VALIDITY = Duration.ofMinutes(15);

    private final WorkerFaceEnrollmentRepository enrollments;
    private final FaceAuthPort faceAuthPort;
    private final FaceEnrollmentPhotoSupport photoSupport;
    private final DocumentService documentService;
    private final FaceIdentifyProperties identifyProperties;
    private final Clock clock;

    FaceAuthServiceImpl(WorkerFaceEnrollmentRepository enrollments, FaceAuthPort faceAuthPort,
                        FaceEnrollmentPhotoSupport photoSupport, DocumentService documentService,
                        FaceIdentifyProperties identifyProperties, Clock clock) {
        this.enrollments = enrollments;
        this.faceAuthPort = faceAuthPort;
        this.photoSupport = photoSupport;
        this.documentService = documentService;
        this.identifyProperties = identifyProperties;
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

    @Override
    @Transactional(readOnly = true)
    public FaceIdentificationResult identify(byte[] imageBytes, String contentType) {
        photoSupport.validate(contentType, imageBytes);

        List<WorkerFaceEnrollment> candidates = enrollments.findAll();
        if (candidates.isEmpty()) {
            throw new BusinessRuleException("There is no one enrolled yet to compare against.");
        }

        FaceEmbeddingResult probe = faceAuthPort.embed(imageBytes, contentType);
        if (probe.embedding() == null || probe.embedding().length != EMBEDDING_DIMENSIONS) {
            throw new BusinessRuleException("Face-auth service returned an unexpected embedding size.");
        }
        if (!probe.quality().acceptable()) {
            throw new BusinessRuleException("Image quality is insufficient to identify a worker"
                    + (probe.quality().reasons().isEmpty() ? "." : ": " + String.join(", ", probe.quality().reasons())));
        }
        if (!probe.live()) {
            throw new BusinessRuleException("Liveness check failed - please retake the photo in person.");
        }

        List<FaceIdentificationCandidate> ranked = candidates.stream()
                .map(c -> new FaceIdentificationCandidate(c.getWorkerId(), cosineSimilarity(probe.embedding(), c.getEmbedding())))
                .sorted(Comparator.comparingDouble(FaceIdentificationCandidate::score).reversed())
                .toList();

        FaceIdentificationCandidate best = ranked.get(0);
        double runnerUpScore = ranked.size() > 1 ? ranked.get(1).score() : Double.NEGATIVE_INFINITY;
        boolean ambiguous = (best.score() - runnerUpScore) < identifyProperties.margin();
        boolean matched = !ambiguous && best.score() >= identifyProperties.threshold();

        List<FaceIdentificationCandidate> topMatches = ranked.stream().limit(MAX_TOP_MATCHES).toList();
        return new FaceIdentificationResult(matched, matched ? best.workerId() : null, best.score(), ambiguous, topMatches);
    }

    /**
     * True cosine similarity (dot product / product of magnitudes), not a bare dot product - correct
     * regardless of whether the face-auth provider's embeddings happen to already be unit-length
     * (legacy's own provider apparently returns pre-normalized vectors, letting its code skip this
     * division safely; this codebase's provider contract was never independently confirmed to do the
     * same - see {@code PythonFaceAuthAdapter}'s Javadoc - so this does not assume it).
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private FaceEnrollmentDto toDto(WorkerFaceEnrollment e) {
        String photoUrl = e.getEnrollPhotoDocumentId() == null ? null
                : documentService.generateDownloadUrl(e.getEnrollPhotoDocumentId(), PHOTO_URL_VALIDITY).toString();
        return new FaceEnrollmentDto(e.getWorkerId(), true, e.getEnrolledAt(), e.getLivenessConfidence(), photoUrl);
    }
}
