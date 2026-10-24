package in.anytimework.atwbackend.document.application;

import java.net.URI;
import java.time.Duration;

/**
 * Port for binary file storage. The business layer talks only to this interface; the concrete
 * backend (Azure Blob / Azurite today, something else tomorrow) lives behind it as an adapter,
 * selected by configuration. This is the vendor-independence boundary (CLAUDE.md §4).
 */
public interface FileStorage {

    /** Store (or overwrite) the bytes at {@code key}. */
    void upload(String key, byte[] data, String contentType);

    /** Read the bytes at {@code key}. */
    byte[] download(String key);

    /**
     * Produce a short-lived, directly-downloadable URL for {@code key}, so large files are served
     * straight from storage instead of streaming through the application.
     */
    URI generateDownloadUrl(String key, Duration validFor);

    void delete(String key);

    boolean exists(String key);
}
