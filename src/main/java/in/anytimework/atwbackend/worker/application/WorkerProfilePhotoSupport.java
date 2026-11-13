package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.ImageMagicBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Worker profile photo upload - an identity photo, not a KYC evidence document, so it gets the same
 * small, image-only allowance as masterdata's icons ({@code MasterDataImageSupport}) rather than the
 * document module's larger, PDF-inclusive limits used for KYC scans.
 */
@Component
class WorkerProfilePhotoSupport {

    private static final Logger log = LoggerFactory.getLogger(WorkerProfilePhotoSupport.class);
    private static final String CATEGORY = "WORKER_PROFILE_PHOTO";
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final DocumentService documents;

    WorkerProfilePhotoSupport(DocumentService documents) {
        this.documents = documents;
    }

    UUID replace(UUID workerId, UUID previousDocumentId, String originalFilename, String declaredContentType, byte[] content) {
        validate(declaredContentType, content);
        DocumentDto stored = documents.upload(new UploadDocumentCommand(CATEGORY, workerId, originalFilename, declaredContentType, content));
        if (previousDocumentId != null) {
            try {
                documents.delete(previousDocumentId);
            } catch (RuntimeException e) {
                log.warn("Failed to delete superseded profile photo document {}; leaving it orphaned for manual cleanup",
                        previousDocumentId, e);
            }
        }
        return stored.id();
    }

    void remove(UUID documentId) {
        if (documentId != null) {
            documents.delete(documentId);
        }
    }

    private void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("Image content must not be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ValidationException("Image exceeds the maximum allowed size of " + MAX_BYTES + " bytes");
        }
        String normalized = contentType == null ? null : contentType.toLowerCase(Locale.ROOT).trim();
        if (normalized == null || !ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new ValidationException(
                    "Unsupported image content type: " + contentType + " (allowed: image/png, image/jpeg, image/webp)");
        }
        ImageMagicBytes.verifyIfRecognizedImageType(contentType, content);
    }
}
