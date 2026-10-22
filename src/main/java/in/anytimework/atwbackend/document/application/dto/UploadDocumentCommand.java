package in.anytimework.atwbackend.document.application.dto;

import java.util.UUID;

/**
 * Request to store a file.
 *
 * @param category         caller-defined grouping (e.g. "KYC", "WORKER_DOCUMENT")
 * @param ownerId          optional id of the owning business entity
 * @param originalFilename the user-facing filename (kept as metadata only)
 * @param contentType      MIME type
 * @param content          the file bytes
 */
public record UploadDocumentCommand(
        String category,
        UUID ownerId,
        String originalFilename,
        String contentType,
        byte[] content
) {
}
