package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the icon-upload validation (no Spring, no real storage) - this is the one place
 * guarding against a spoofed Content-Type header, since AzureBlobFileStorage trusts and re-serves
 * whatever content-type it's given (see the class Javadoc on MasterDataImageSupport).
 */
class MasterDataImageSupportTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x00};
    private static final byte[] WEBP_MAGIC =
            {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] NOT_AN_IMAGE = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
    // RIFF container but WAVE, not WEBP, at the FourCC offset (bytes 8-11) - a valid audio file that
    // must still be rejected when declared as image/webp.
    private static final byte[] RIFF_WAVE_NOT_WEBP =
            {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45};

    private final DocumentService documents = mock(DocumentService.class);
    private final MasterDataImageSupport images = new MasterDataImageSupport(documents);

    @Test
    void replace_withValidPng_uploadsAndReturnsNewId() {
        UUID newId = UUID.randomUUID();
        when(documents.upload(any())).thenReturn(new DocumentDto(newId, "icon.png", "image/png", PNG_MAGIC.length,
                "INDUSTRY_ICON", null, Instant.now()));

        UUID result = images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.png", "image/png", PNG_MAGIC);

        assertThat(result).isEqualTo(newId);
        verify(documents, never()).delete(any());
    }

    @Test
    void replace_withValidJpeg_succeeds() {
        when(documents.upload(any())).thenReturn(new DocumentDto(UUID.randomUUID(), "icon.jpg", "image/jpeg",
                JPEG_MAGIC.length, "SKILL_ICON", null, Instant.now()));

        assertThat(images.replace("SKILL_ICON", UUID.randomUUID(), null, "icon.jpg", "image/jpeg", JPEG_MAGIC))
                .isNotNull();
    }

    @Test
    void replace_withValidWebp_succeeds() {
        when(documents.upload(any())).thenReturn(new DocumentDto(UUID.randomUUID(), "icon.webp", "image/webp",
                WEBP_MAGIC.length, "DESIGNATION_ICON", null, Instant.now()));

        assertThat(images.replace("DESIGNATION_ICON", UUID.randomUUID(), null, "icon.webp", "image/webp", WEBP_MAGIC))
                .isNotNull();
    }

    @Test
    void replace_whenContentDoesNotMatchDeclaredType_isRejectedBeforeUpload() {
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "fake.png", "image/png",
                NOT_AN_IMAGE))
                .isInstanceOf(ValidationException.class);

        verify(documents, never()).upload(any());
    }

    @Test
    void replace_withDisallowedContentType_isRejected() {
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "doc.pdf",
                "application/pdf", PNG_MAGIC))
                .isInstanceOf(ValidationException.class);

        verify(documents, never()).upload(any());
    }

    @Test
    void replace_withRiffContainerThatIsNotWebp_isRejected() {
        // Guards against treating any RIFF file (WAV, AVI, ...) as WEBP just because it shares the
        // generic 4-byte container signature - only the WEBP FourCC at bytes 8-11 actually confirms it.
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "fake.webp",
                "image/webp", RIFF_WAVE_NOT_WEBP))
                .isInstanceOf(ValidationException.class);

        verify(documents, never()).upload(any());
    }

    @Test
    void replace_withNullContentType_isRejectedCleanly_notWithANullPointerException() {
        // MultipartFile.getContentType() legitimately returns null when a client omits the header;
        // Map.of(...).get(null) throws NPE rather than returning null, so this must be guarded explicitly.
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.png", null, PNG_MAGIC))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void replace_withUppercaseContentType_isStillAccepted() {
        when(documents.upload(any())).thenReturn(new DocumentDto(UUID.randomUUID(), "icon.png", "image/png",
                PNG_MAGIC.length, "INDUSTRY_ICON", null, Instant.now()));

        assertThat(images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.png", "IMAGE/PNG", PNG_MAGIC))
                .isNotNull();
    }

    @Test
    void replace_withSvg_isRejected_evenThoughItIsAnImageType() {
        // SVG can embed <script>; AzureBlobFileStorage would serve it back verbatim - see Javadoc.
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.svg",
                "image/svg+xml", PNG_MAGIC))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void replace_withEmptyContent_isRejected() {
        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.png", "image/png",
                new byte[0]))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void replace_withOversizedContent_isRejected() {
        byte[] oversized = new byte[3 * 1024 * 1024];
        System.arraycopy(PNG_MAGIC, 0, oversized, 0, PNG_MAGIC.length);

        assertThatThrownBy(() -> images.replace("INDUSTRY_ICON", UUID.randomUUID(), null, "icon.png", "image/png",
                oversized))
                .isInstanceOf(ValidationException.class);

        verify(documents, never()).upload(any());
    }

    @Test
    void replace_withPreviousImage_deletesItAfterTheNewOneIsStored() {
        UUID previousId = UUID.randomUUID();
        when(documents.upload(any())).thenReturn(new DocumentDto(UUID.randomUUID(), "icon.png", "image/png",
                PNG_MAGIC.length, "INDUSTRY_ICON", null, Instant.now()));

        images.replace("INDUSTRY_ICON", UUID.randomUUID(), previousId, "icon.png", "image/png", PNG_MAGIC);

        verify(documents).delete(previousId);
    }

    @Test
    void replace_whenDeletingThePreviousImageFails_stillReturnsTheNewId() {
        UUID previousId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        when(documents.upload(any())).thenReturn(new DocumentDto(newId, "icon.png", "image/png", PNG_MAGIC.length,
                "INDUSTRY_ICON", null, Instant.now()));
        doThrow(new RuntimeException("storage outage")).when(documents).delete(previousId);

        UUID result = images.replace("INDUSTRY_ICON", UUID.randomUUID(), previousId, "icon.png", "image/png", PNG_MAGIC);

        assertThat(result).isEqualTo(newId);
    }

    @Test
    void remove_withNullDocumentId_doesNothing() {
        images.remove(null);

        verify(documents, never()).delete(any());
    }

    @Test
    void remove_withDocumentId_delegatesToDocumentService() {
        UUID id = UUID.randomUUID();

        images.remove(id);

        verify(documents).delete(id);
    }

    @Test
    void urlOrNull_withNullDocumentId_returnsNull() {
        assertThat(images.urlOrNull(null)).isNull();
    }

    @Test
    void urlOrNull_withDocumentId_resolvesAShortLivedUrl() {
        UUID id = UUID.randomUUID();
        when(documents.generateDownloadUrl(eq(id), any(Duration.class))).thenReturn(URI.create("https://example/x"));

        assertThat(images.urlOrNull(id)).isEqualTo("https://example/x");
    }
}
