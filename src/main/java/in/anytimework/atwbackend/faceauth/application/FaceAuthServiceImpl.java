package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationCandidate;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationResult;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class FaceAuthServiceImpl implements FaceAuthService {

    private static final Logger log = LoggerFactory.getLogger(FaceAuthServiceImpl.class);
    private static final int EMBEDDING_DIMENSIONS = 128;
    private static final int MAX_TOP_MATCHES = 5;
    private static final Duration PHOTO_URL_VALIDITY = Duration.ofMinutes(15);

    private final WorkerFaceEnrollmentRepository enrollments;
    private final FaceAuthPort faceAuthPort;
    private final FaceEnrollmentPhotoSupport photoSupport;
    private final DocumentService documentService;
    private final FaceIdentifyProperties identifyProperties;
    private final FaceDuplicateProperties duplicateProperties;
    private final WorkerDisplayLookup workerDisplayLookup;
    private final Clock clock;

    FaceAuthServiceImpl(WorkerFaceEnrollmentRepository enrollments, FaceAuthPort faceAuthPort,
                        FaceEnrollmentPhotoSupport photoSupport, DocumentService documentService,
                        FaceIdentifyProperties identifyProperties, FaceDuplicateProperties duplicateProperties,
                        WorkerDisplayLookup workerDisplayLookup, Clock clock) {
        this.enrollments = enrollments;
        this.faceAuthPort = faceAuthPort;
        this.photoSupport = photoSupport;
        this.documentService = documentService;
        this.identifyProperties = identifyProperties;
        this.duplicateProperties = duplicateProperties;
        this.workerDisplayLookup = workerDisplayLookup;
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
    @Transactional(readOnly = true)
    public Set<UUID> findEnrolledWorkerIds() {
        return enrollments.findAllWorkerIds();
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
        rejectIfDuplicate(workerId, result.embedding());

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
    @Transactional
    public void excludeFromMatching(UUID workerId) {
        enrollments.findByWorkerId(workerId).ifPresent(e -> {
            e.excludeFromMatching(clock.instant());
            enrollments.save(e);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public FaceIdentificationResult identify(byte[] imageBytes, String contentType) {
        photoSupport.validate(contentType, imageBytes);

        List<WorkerFaceEnrollment> candidates = enrollments.findByExcludedFromMatchingAtIsNull();
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

        List<RankedMatch> ranked = rankCandidates(probe.embedding(), candidates);

        RankedMatch best = ranked.get(0);
        double runnerUpScore = ranked.size() > 1 ? ranked.get(1).score() : Double.NEGATIVE_INFINITY;
        boolean ambiguous = (best.score() - runnerUpScore) < identifyProperties.margin();
        boolean matched = !ambiguous && best.score() >= identifyProperties.threshold();

        List<RankedMatch> topRanked = ranked.stream().limit(MAX_TOP_MATCHES).toList();
        Map<UUID, WorkerDisplayInfo> displayInfoById = workerDisplayLookup.findByIds(
                topRanked.stream().map(RankedMatch::workerId).collect(Collectors.toSet()));
        List<FaceIdentificationCandidate> topMatches = topRanked.stream()
                .map(r -> new FaceIdentificationCandidate(r.workerId(), nameOf(displayInfoById, r.workerId()), r.score()))
                .toList();
        String matchedName = matched ? nameOf(displayInfoById, best.workerId()) : null;
        return new FaceIdentificationResult(matched, matched ? best.workerId() : null, matchedName, best.score(),
                ambiguous, topMatches);
    }

    /**
     * Hard-rejects enrollment (throws {@link ConflictException}, nothing persisted) if {@code newEmbedding}
     * confidently matches an already-enrolled, not-excluded DIFFERENT worker - same unconditional
     * every-enroll check and same outright-reject behavior as legacy's
     * {@code FaceAuthService::findDuplicateFace}: nothing is saved, and the error names the
     * conflicting worker - resolved to a display name/code via {@link WorkerDisplayLookup}, never a
     * raw id (see that interface's Javadoc for how this module reaches worker data without a cyclic
     * module dependency) - plus the similarity score, so the admin can find that worker and decide
     * what to do. {@code workerId} itself is excluded from the comparison set in Java (not by the
     * repository query) - re-enrolling yourself must never trip over your own prior photo, exactly
     * like legacy excludes {@code excludeWorkerId}.
     */
    private void rejectIfDuplicate(UUID workerId, float[] newEmbedding) {
        List<WorkerFaceEnrollment> others = enrollments.findByExcludedFromMatchingAtIsNull().stream()
                .filter(e -> !e.getWorkerId().equals(workerId))
                .toList();
        if (others.isEmpty()) {
            return;
        }
        RankedMatch best = rankCandidates(newEmbedding, others).get(0);
        if (best.score() >= duplicateProperties.threshold()) {
            WorkerDisplayInfo info = workerDisplayLookup.findByIds(Set.of(best.workerId())).get(best.workerId());
            String reference;
            if (info != null) {
                reference = info.fullName() + " (" + info.workerCode() + ")";
            } else {
                log.warn("Duplicate-face check matched enrolled workerId {} that WorkerDisplayLookup could not "
                        + "resolve to a name - showing a generic reference instead.", best.workerId());
                reference = "another enrolled worker";
            }
            throw new ConflictException(String.format(Locale.ROOT,
                    "This face is already enrolled to %s, with a similarity score of %.2f - a face can only be "
                            + "enrolled once.", reference, best.score()));
        }
    }

    /**
     * Shared by {@link #identify} and {@link #rejectIfDuplicate} - both are "rank every candidate by
     * cosine similarity to one probe embedding", just applied to a different candidate set and
     * decision. Internal only: unlike the public {@link FaceIdentificationCandidate}, never carries a
     * resolved name - a per-candidate lookup here would mean one {@link WorkerDisplayLookup} call per
     * candidate; callers batch-resolve names once, after ranking, instead.
     */
    private static List<RankedMatch> rankCandidates(float[] probeEmbedding, List<WorkerFaceEnrollment> candidates) {
        return candidates.stream()
                .map(c -> new RankedMatch(c.getWorkerId(), cosineSimilarity(probeEmbedding, c.getEmbedding())))
                .sorted(Comparator.comparingDouble(RankedMatch::score).reversed())
                .toList();
    }

    private record RankedMatch(UUID workerId, double score) {
    }

    private static String nameOf(Map<UUID, WorkerDisplayInfo> displayInfoById, UUID workerId) {
        WorkerDisplayInfo info = displayInfoById.get(workerId);
        return info == null ? null : info.fullName();
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
