package in.anytimework.atwbackend.document.infrastructure;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import in.anytimework.atwbackend.document.application.FileStorage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Azure Blob implementation of {@link FileStorage}. This is the only class in the codebase that
 * knows about the Azure Storage SDK; swapping providers means writing one more adapter, not
 * touching any business code.
 */
@Component
public class AzureBlobFileStorage implements FileStorage {

    private final BlobServiceClient serviceClient;
    private final BlobContainerClient containerClient;
    private final boolean accountKeyAvailable;

    private volatile boolean containerReady = false;

    public AzureBlobFileStorage(BlobServiceClient serviceClient, StorageProperties properties) {
        this.serviceClient = serviceClient;
        this.containerClient = serviceClient.getBlobContainerClient(properties.container());
        // With a connection string we hold the account key, so we can sign SAS tokens directly.
        // Without it (managed identity) we must request a user-delegation key instead.
        this.accountKeyAvailable = StringUtils.hasText(properties.connectionString());
    }

    @Override
    public void upload(String key, byte[] data, String contentType) {
        ensureContainer();
        BlobClient blob = containerClient.getBlobClient(key);
        blob.upload(BinaryData.fromBytes(data), true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
    }

    @Override
    public byte[] download(String key) {
        return containerClient.getBlobClient(key).downloadContent().toBytes();
    }

    @Override
    public URI generateDownloadUrl(String key, Duration validFor) {
        BlobClient blob = containerClient.getBlobClient(key);
        OffsetDateTime expiry = OffsetDateTime.now().plus(validFor);
        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiry, permission);

        String sas;
        if (accountKeyAvailable) {
            sas = blob.generateSas(values);
        } else {
            UserDelegationKey delegationKey = serviceClient.getUserDelegationKey(OffsetDateTime.now(), expiry);
            sas = blob.generateUserDelegationSas(values, delegationKey);
        }
        return URI.create(blob.getBlobUrl() + "?" + sas);
    }

    @Override
    public void delete(String key) {
        containerClient.getBlobClient(key).deleteIfExists();
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(containerClient.getBlobClient(key).exists());
    }

    /** Create the container on first use (idempotent); avoids a network call at startup. */
    private void ensureContainer() {
        if (!containerReady) {
            synchronized (this) {
                if (!containerReady) {
                    containerClient.createIfNotExists();
                    containerReady = true;
                }
            }
        }
    }
}
