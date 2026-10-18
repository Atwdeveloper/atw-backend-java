package in.anytimework.atwbackend.document.application;

import in.anytimework.atwbackend.document.application.dto.DocumentContent;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.document.domain.Document;
import in.anytimework.atwbackend.document.domain.DocumentRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository repository;
    private final FileStorage fileStorage;
    private final DocumentProperties properties;

    DocumentServiceImpl(DocumentRepository repository, FileStorage fileStorage, DocumentProperties properties) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.properties = properties;
    }

    @Override
    @Transactional
    public DocumentDto upload(UploadDocumentCommand command) {
        validate(command);
        String storageKey = buildStorageKey(command);
        String checksum = sha256(command.content());

        // Persist metadata first: if the blob upload then fails, the transaction rolls back and we
        // are left with neither a row nor an orphaned blob.
        Document document = repository.save(new Document(
                storageKey,
                command.originalFilename(),
                command.contentType(),
                command.content().length,
                checksum,
                command.category(),
                command.ownerId()));

        fileStorage.upload(storageKey, command.content(), command.contentType());
        return toDto(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent download(UUID documentId) {
        Document document = getOrThrow(documentId);
        byte[] data = fileStorage.download(document.getStorageKey());
        return new DocumentContent(document.getOriginalFilename(), document.getContentType(), data);
    }

    @Override
    @Transactional(readOnly = true)
    public URI generateDownloadUrl(UUID documentId, Duration validFor) {
        Document document = getOrThrow(documentId);
        return fileStorage.generateDownloadUrl(document.getStorageKey(), validFor);
    }

    @Override
    @Transactional
    public void delete(UUID documentId) {
        Document document = getOrThrow(documentId);
        fileStorage.delete(document.getStorageKey());
        repository.delete(document);
    }

    private Document getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private void validate(UploadDocumentCommand command) {
        if (command.content() == null || command.content().length == 0) {
            throw new ValidationException("File content must not be empty");
        }
        if (command.content().length > properties.maxSizeBytes()) {
            throw new ValidationException(
                    "File exceeds the maximum allowed size of " + properties.maxSizeBytes() + " bytes");
        }
        if (!properties.allowedContentTypes().contains(command.contentType())) {
            throw new ValidationException("Unsupported content type: " + command.contentType());
        }
    }

    private String buildStorageKey(UploadDocumentCommand command) {
        String owner = command.ownerId() != null ? command.ownerId().toString() : "shared";
        return command.category().toLowerCase(Locale.ROOT) + "/" + owner + "/"
                + UUID.randomUUID() + extension(command.originalFilename());
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private DocumentDto toDto(Document d) {
        return new DocumentDto(d.getId(), d.getOriginalFilename(), d.getContentType(),
                d.getSizeBytes(), d.getCategory(), d.getOwnerId(), d.getCreatedAt());
    }
}
