package in.anytimework.atwbackend.document.infrastructure;

import in.anytimework.atwbackend.document.application.DocumentProperties;
import in.anytimework.atwbackend.document.application.FileStorage;
import in.anytimework.atwbackend.document.domain.Document;
import in.anytimework.atwbackend.document.domain.DocumentRepository;
import in.anytimework.atwbackend.document.domain.DocumentUploadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Heals {@link Document} rows left stuck {@code PENDING}: {@code DocumentServiceImpl.upload()}
 * commits the row, then uploads the blob, then commits the {@code COMPLETE} flip in a second,
 * independent transaction. If that final commit fails after the upload already succeeded, the row
 * stays {@code PENDING} forever with a real blob behind it (an invisible orphan) unless something
 * resolves it. This job finds rows that have been {@code PENDING} for longer than
 * {@code atw.documents.reconciliation-stale-after} and resolves each one by checking whether its
 * blob actually exists.
 */
@Component
class DocumentUploadReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadReconciliationJob.class);

    private final DocumentRepository repository;
    private final FileStorage fileStorage;
    private final DocumentProperties properties;
    private final Clock clock;

    DocumentUploadReconciliationJob(DocumentRepository repository, FileStorage fileStorage,
                                     DocumentProperties properties, Clock clock) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Deliberately NOT one {@code @Transactional} method wrapping the whole loop: each row's
     * {@code repository.save(...)} is transactional per-call on its own (Spring Data wraps every
     * inherited method), so one row losing an optimistic-lock race against another app instance's
     * concurrent reconciliation pass only skips that row — it can't roll back every other row this
     * pass already resolved, and the lost row simply gets picked up again (or is already resolved)
     * on the next scheduled run.
     */
    @Scheduled(fixedDelayString = "${atw.documents.reconciliation-interval}")
    void reconcile() {
        Instant threshold = clock.instant().minus(properties.reconciliationStaleAfter());
        List<Document> stale =
                repository.findByUploadStatusAndCreatedAtBefore(DocumentUploadStatus.PENDING, threshold);

        int resolved = 0;
        for (Document document : stale) {
            try {
                if (fileStorage.exists(document.getStorageKey())) {
                    document.markComplete();
                } else {
                    document.markFailed();
                }
                repository.save(document);
                resolved++;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Skipped a document reconciliation row already resolved concurrently: {}",
                        document.getId());
            }
        }

        if (resolved > 0) {
            log.info("Document upload reconciliation resolved {} stale pending row(s)", resolved);
        }
    }
}
