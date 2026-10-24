package in.anytimework.atwbackend.document.infrastructure;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test of the Azure Blob adapter against a real Azurite container.
 * Skips automatically when Docker is unavailable (e.g. this Windows shell); runs in CI / WSL.
 */
@Testcontainers(disabledWithoutDocker = true)
class AzureBlobFileStorageIT {

    // Azurite's well-known development account (public emulator credentials, not a secret).
    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Container
    static final GenericContainer<?> AZURITE =
            new GenericContainer<>(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
                    .withExposedPorts(10000)
                    .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck");

    private static String connectionString() {
        return "DefaultEndpointsProtocol=http;AccountName=" + ACCOUNT_NAME + ";AccountKey=" + ACCOUNT_KEY
                + ";BlobEndpoint=http://" + AZURITE.getHost() + ":" + AZURITE.getMappedPort(10000)
                + "/" + ACCOUNT_NAME + ";";
    }

    @Test
    void uploadDownloadUrlDeleteRoundTrip() {
        String connectionString = connectionString();
        BlobServiceClient serviceClient =
                new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        AzureBlobFileStorage storage = new AzureBlobFileStorage(
                serviceClient, new StorageProperties(connectionString, null, "it-documents"));

        String key = "test/" + UUID.randomUUID() + ".txt";
        byte[] data = "hello azurite".getBytes(StandardCharsets.UTF_8);

        storage.upload(key, data, "text/plain");
        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.download(key)).isEqualTo(data);

        URI url = storage.generateDownloadUrl(key, Duration.ofMinutes(5));
        // The key's '/' is percent-encoded in the blob URL; assert it's a signed URL for our container.
        assertThat(url.toString()).contains("it-documents").contains("sig=");

        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }
}
