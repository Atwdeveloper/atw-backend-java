package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared icon/image handling for industry/designation/skill, built on the document module's public
 * {@link DocumentService} (the same cross-module shape {@code identity} already uses for
 * {@code notification}) - masterdata never touches blob storage directly.
 *
 * <p>Deliberately stricter than the document module's own general-purpose limits
 * ({@code atw.documents.*} allows PDFs up to 10MB, meant for KYC-grade uploads): an icon has no
 * business being a PDF or being large, so this rejects anything outside a small image allow-list
 * before it ever reaches {@link DocumentService}.</p>
 *
 * <p>The content-type is verified against the file's actual leading bytes, not just the caller's
 * declared header - {@link in.anytimework.atwbackend.document.infrastructure.AzureBlobFileStorage}
 * stores and re-serves whatever content-type it's given with no sniffing of its own, so a spoofed
 * header (e.g. an HTML/SVG payload declared as {@code image/png}) would otherwise be served back
 * as-is and could execute in a browser that opens the image URL directly.</p>
 */
@Component
class MasterDataImageSupport {

    private static final Logger log = LoggerFactory.getLogger(MasterDataImageSupport.class);

    private static final long MAX_BYTES = 2L * 1024 * 1024; // 2MB - an icon, not a document scan
    private static final Duration URL_VALIDITY = Duration.ofMinutes(15);

    // SVG is deliberately excluded even though it's an "image" type: it can embed <script> and would
    // be served back and rendered same-origin, a stored-XSS vector - see the class Javadoc.
    private static final Map<String, byte[]> MAGIC_BYTES_BY_CONTENT_TYPE = Map.of(
            "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    );
    // WEBP is a RIFF container, but "RIFF" alone (bytes 0-3) is shared by WAV/AVI/etc. - the format
    // itself is only identified by the "WEBP" FourCC at bytes 8-11, so both must match.
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_FOURCC = {0x57, 0x45, 0x42, 0x50};
    private static final int WEBP_FOURCC_OFFSET = 8;

    private final DocumentService documents;

    MasterDataImageSupport(DocumentService documents) {
        this.documents = documents;
    }

    /** Stores the new image, then best-effort deletes the previous one (non-fatal if that fails). */
    UUID replace(String category, UUID ownerId, UUID previousDocumentId, String originalFilename,
                 String declaredContentType, byte[] content) {
        validate(declaredContentType, content);
        DocumentDto stored = documents.upload(
                new UploadDocumentCommand(category, ownerId, originalFilename, declaredContentType, content));
        if (previousDocumentId != null) {
            try {
                documents.delete(previousDocumentId);
            } catch (RuntimeException e) {
                log.warn("Failed to delete superseded image document {}; leaving it orphaned for manual cleanup",
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

    String urlOrNull(UUID documentId) {
        return documentId == null ? null : documents.generateDownloadUrl(documentId, URL_VALIDITY).toString();
    }

    private void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("Image content must not be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ValidationException("Image exceeds the maximum allowed size of " + MAX_BYTES + " bytes");
        }
        // Normalized (lower-cased) for matching only - a client declaring "image/PNG" or omitting the
        // header (contentType == null, legal in multipart/form-data) must not bypass or crash this
        // check; the original, unnormalized value is kept for the error message shown back to them.
        String normalized = contentType == null ? null : contentType.toLowerCase(Locale.ROOT).trim();
        if ("image/webp".equals(normalized)) {
            if (!matchesAt(content, 0, RIFF) || !matchesAt(content, WEBP_FOURCC_OFFSET, WEBP_FOURCC)) {
                throw new ValidationException("File content does not match declared content type image/webp");
            }
            return;
        }
        // Map.of(...)'s immutable map throws NPE on get(null) rather than returning null like
        // HashMap - guard explicitly so a missing Content-Type reports as a clean 400, not a 500.
        byte[] expectedMagic = normalized == null ? null : MAGIC_BYTES_BY_CONTENT_TYPE.get(normalized);
        if (expectedMagic == null) {
            throw new ValidationException(
                    "Unsupported image content type: " + contentType + " (allowed: image/png, image/jpeg, image/webp)");
        }
        if (!matchesAt(content, 0, expectedMagic)) {
            throw new ValidationException("File content does not match declared content type " + normalized);
        }
    }

    private boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
