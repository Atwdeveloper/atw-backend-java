package in.anytimework.atwbackend.document.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure Blob connection settings, bound from {@code atw.storage.azure.*}.
 *
 * <p>Provide either a {@code connectionString} (local Azurite, or any key-based setup) or an
 * {@code endpoint} (cloud, authenticated with managed identity). {@code container} is the blob
 * container all documents live in.</p>
 */
@ConfigurationProperties(prefix = "atw.storage.azure")
public record StorageProperties(String connectionString, String endpoint, String container) {
}
