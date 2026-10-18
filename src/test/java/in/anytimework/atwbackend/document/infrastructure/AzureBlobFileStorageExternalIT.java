package in.anytimework.atwbackend.document.infrastructure;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips the Azure Blob adapter against an already-running Azurite reachable over the network
 * (e.g. started via {@code docker compose up -d}, or in WSL with port 10000 forwarded to the Windows
 * host). Dormant by default; enable by passing the blob endpoint, e.g.:
 *
 * <pre>./mvnw test -Dtest=AzureBlobFileStorageExternalIT -Dazurite.endpoint=http://127.0.0.1:10000</pre>
 *
 * Unlike {@link AzureBlobFileStorageIT} (which starts its own container and needs Docker reachable
 * from the JVM), this one verifies the adapter where only the container port is reachable.
 */
@EnabledIfSystemProperty(named = "azurite.endpoint", matches = ".+")
class AzureBlobFileStorageExternalIT {

    // Azurite's well-known development account (public emulator credentials, not a secret).
    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Test
    void uploadDownloadUrlDeleteRoundTrip() {
        String endpoint = System.getProperty("azurite.endpoint"); // e.g. http://127.0.0.1:10000
        String connectionString = "DefaultEndpointsProtocol=http;AccountName=" + ACCOUNT_NAME
                + ";AccountKey=" + ACCOUNT_KEY + ";BlobEndpoint=" + endpoint + "/" + ACCOUNT_NAME + ";";

        BlobServiceClient serviceClient =
                new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        AzureBlobFileStorage storage = new AzureBlobFileStorage(
                serviceClient, new StorageProperties(connectionString, null, "ext-it-documents"));

        String key = "test/" + UUID.randomUUID() + ".txt";
        byte[] data = "hello from the real adapter".getBytes(StandardCharsets.UTF_8);

        storage.upload(key, data, "text/plain");
        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.download(key)).isEqualTo(data);

        URI url = storage.generateDownloadUrl(key, Duration.ofMinutes(5));
        // The key's '/' is percent-encoded in the blob URL; assert it's a signed URL for our container.
        assertThat(url.toString()).contains("ext-it-documents").contains("sig=");

        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }
}
