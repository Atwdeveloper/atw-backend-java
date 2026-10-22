package in.anytimework.atwbackend.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByStorageKey(String storageKey);

    List<Document> findByUploadStatusAndCreatedAtBefore(DocumentUploadStatus status, Instant threshold);
}
