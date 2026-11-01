package in.anytimework.atwbackend.document.application;

import in.anytimework.atwbackend.document.application.dto.DocumentContent;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.document.domain.Document;
import in.anytimework.atwbackend.document.domain.DocumentRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    DocumentServiceImpl(DocumentRepository repository, FileStorage fileStorage, DocumentProperties properties,
                         PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW, not the default REQUIRED: this must genuinely commit on its own even when a
        // caller (e.g. masterdata's icon-upload services) invokes upload()/delete() from inside its
        // own @Transactional method. Under the default propagation the two execute() calls below
        // would silently join that caller's transaction instead of committing independently, so a
        // later failure in the *caller's* unrelated work would roll back the PENDING->COMPLETE flip
        // (or the delete) right along with it - after the blob itself was already written/removed in
        // storage, which no transaction can undo. That defeats the whole point of the split below.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Split into two independently-committed transactions around the blob upload: if the final
    // commit (the PENDING -> COMPLETE flip) fails after the upload already succeeded, it leaves a
    // detectable PENDING row instead of a silent, untracked orphan blob, which the reconciliation
    // job (DocumentUploadReconciliationJob) then heals.
    @Override
    public DocumentDto upload(UploadDocumentCommand command) {
        validate(command);
        String storageKey = buildStorageKey(command);
        String checksum = sha256(command.content());

        Document document = transactionTemplate.execute(status -> repository.save(new Document(
                storageKey,
                command.originalFilename(),
                command.contentType(),
                command.content().length,
                checksum,
                command.category(),
                command.ownerId())));

        fileStorage.upload(storageKey, command.content(), command.contentType());

        transactionTemplate.executeWithoutResult(status -> {
            document.markComplete();
            repository.save(document);
        });

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

    // REQUIRES_NEW for the same reason as the upload() TransactionTemplate above: the blob delete is
    // a real, unrollback-able side effect, so this must commit independently of whatever transaction
    // the caller might already be in - otherwise a later failure in the caller's own work rolls back
    // the row deletion while the blob stays gone, leaving a dangling reference to a 404ing blob.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
