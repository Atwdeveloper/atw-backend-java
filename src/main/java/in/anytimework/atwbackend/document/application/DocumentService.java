package in.anytimework.atwbackend.document.application;

import in.anytimework.atwbackend.document.application.dto.DocumentContent;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Public API of the document module. Other modules (KYC, worker, company, ...) call this to store
 * and retrieve files; they keep only the returned document id and never touch storage directly.
 */
public interface DocumentService {

    DocumentDto upload(UploadDocumentCommand command);

    DocumentContent download(UUID documentId);

    URI generateDownloadUrl(UUID documentId, Duration validFor);

    void delete(UUID documentId);
}
