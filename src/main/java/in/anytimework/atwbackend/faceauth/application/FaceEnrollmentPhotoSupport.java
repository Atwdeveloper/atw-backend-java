package in.anytimework.atwbackend.faceauth.application;

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
 * Stores a worker's face-enrollment reference photo - the same small, image-only allowance as
 * {@code worker.application.WorkerProfilePhotoSupport}, which this otherwise mirrors, except
 * {@link #validate} is exposed separately from {@link #store}: the caller must validate (and run the
 * face-auth service's own quality/liveness check) BEFORE actually uploading, so a rejected photo never
 * gets persisted to blob storage in the first place.
 */
@Component
class FaceEnrollmentPhotoSupport {

    private static final Logger log = LoggerFactory.getLogger(FaceEnrollmentPhotoSupport.class);
    private static final String CATEGORY = "WORKER_FACE_ENROLLMENT_PHOTO";
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final DocumentService documents;

    FaceEnrollmentPhotoSupport(DocumentService documents) {
        this.documents = documents;
    }

    void validate(String contentType, byte[] content) {
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

    UUID store(UUID workerId, UUID previousDocumentId, String originalFilename, String declaredContentType, byte[] content) {
        DocumentDto stored = documents.upload(new UploadDocumentCommand(CATEGORY, workerId, originalFilename, declaredContentType, content));
        if (previousDocumentId != null) {
            try {
                documents.delete(previousDocumentId);
            } catch (RuntimeException e) {
                log.warn("Failed to delete superseded face-enrollment photo document {}; leaving it orphaned for manual cleanup",
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
}
