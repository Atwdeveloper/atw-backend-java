package in.anytimework.atwbackend.document.application;

import in.anytimework.atwbackend.document.application.dto.DocumentContent;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.document.domain.Document;
import in.anytimework.atwbackend.document.domain.DocumentRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the document orchestration logic (no Spring, no Azure). */
class DocumentServiceImplTest {

    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final DocumentProperties properties =
            new DocumentProperties(1_000, List.of("application/pdf", "image/png"),
                    Duration.ofMinutes(10), Duration.ofMinutes(5));
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final DocumentServiceImpl service =
            new DocumentServiceImpl(repository, fileStorage, properties, transactionManager);

    @Test
    void upload_persistsMetadataAndStoresBytes() {
        byte[] bytes = "a tiny pdf".getBytes(StandardCharsets.UTF_8);
        UUID owner = UUID.randomUUID();
        when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentDto dto = service.upload(
                new UploadDocumentCommand("KYC", owner, "pan.pdf", "application/pdf", bytes));

        assertThat(dto.originalFilename()).isEqualTo("pan.pdf");
        assertThat(dto.category()).isEqualTo("KYC");
        assertThat(dto.ownerId()).isEqualTo(owner);
        // Saved twice: once committing the PENDING row before the upload, once committing the
        // COMPLETE flip after it (see DocumentServiceImpl.upload()).
        verify(repository, times(2)).save(any(Document.class));
        // Key is category/owner/uuid.ext; content + type are forwarded unchanged.
        verify(fileStorage).upload(anyString(), eq(bytes), eq("application/pdf"));
    }

    @Test
    void upload_rejectsDisallowedContentType() {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload(
                new UploadDocumentCommand("KYC", null, "x.exe", "application/octet-stream", bytes)))
                .isInstanceOf(ValidationException.class);

        verify(fileStorage, never()).upload(anyString(), any(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void upload_rejectsOversizeFile() {
        byte[] tooBig = new byte[1_001]; // limit is 1_000

        assertThatThrownBy(() -> service.upload(
                new UploadDocumentCommand("KYC", null, "big.pdf", "application/pdf", tooBig)))
                .isInstanceOf(ValidationException.class);

        verify(fileStorage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void download_returnsStoredBytes() {
        UUID id = UUID.randomUUID();
        Document doc = new Document("kyc/x/abc.pdf", "pan.pdf", "application/pdf", 3, "sum", "KYC", null);
        byte[] bytes = "pdf".getBytes(StandardCharsets.UTF_8);
        when(repository.findById(id)).thenReturn(Optional.of(doc));
        when(fileStorage.download("kyc/x/abc.pdf")).thenReturn(bytes);

        DocumentContent content = service.download(id);

        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.data()).isEqualTo(bytes);
    }

    @Test
    void download_missingDocumentThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
