package in.anytimework.atwbackend.document.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Metadata for a stored file. The bytes themselves live in blob storage (keyed by {@link #storageKey});
 * this row records everything needed to find, serve, and audit the file.
 *
 * <p>{@code category} + {@code ownerId} let any module tag a document (e.g. category "KYC",
 * ownerId = the company's id) without this module depending on that module.</p>
 */
@Getter
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private DocumentUploadStatus uploadStatus;

    protected Document() {
        // required by JPA
    }

    public Document(String storageKey, String originalFilename, String contentType, long sizeBytes,
                    String checksumSha256, String category, UUID ownerId) {
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.category = category;
        this.ownerId = ownerId;
        this.uploadStatus = DocumentUploadStatus.PENDING;
    }

    /** Marks the row {@code COMPLETE} once the blob upload and its follow-up commit both succeed. */
    public void markComplete() {
        this.uploadStatus = DocumentUploadStatus.COMPLETE;
    }

    /** Marks the row {@code FAILED}; used by the reconciliation job when no blob ever landed. */
    public void markFailed() {
        this.uploadStatus = DocumentUploadStatus.FAILED;
    }
}
