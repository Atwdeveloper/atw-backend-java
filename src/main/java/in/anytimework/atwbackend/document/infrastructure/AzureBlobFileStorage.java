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

    // How long a fetched user-delegation key is reused before asking Azure AD for a fresh one, and
    // how early to refresh relative to that - comfortably covers every validFor this codebase
    // actually requests (photo/image display URLs, all 15 minutes), so one key signs many SAS tokens
    // instead of one network round-trip per generateDownloadUrl call. Without this, listing endpoints
    // that resolve an image URL per row would make one Azure AD call PER ROW in any environment using
    // managed identity (no account key) - the actual reason list views used to omit images entirely.
    private static final Duration DELEGATION_KEY_LIFETIME = Duration.ofHours(1);
    private static final Duration DELEGATION_KEY_REFRESH_MARGIN = Duration.ofMinutes(10);

    private final BlobServiceClient serviceClient;
    private final BlobContainerClient containerClient;
    private final boolean accountKeyAvailable;

    private volatile boolean containerReady = false;
    private volatile CachedDelegationKey cachedDelegationKey;

    private record CachedDelegationKey(UserDelegationKey key, OffsetDateTime expiry) {
    }

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
            sas = blob.generateUserDelegationSas(values, currentDelegationKey());
        }
        return URI.create(blob.getBlobUrl() + "?" + sas);
    }

    /**
     * Reuses a cached delegation key for up to {@link #DELEGATION_KEY_LIFETIME}, refreshing it
     * {@link #DELEGATION_KEY_REFRESH_MARGIN} early - the same double-checked-locking shape as
     * {@link #ensureContainer()}. Assumes every caller's {@code validFor} stays well under the cached
     * key's own remaining lifetime (true today - 15 minutes for every photo/image URL this codebase
     * generates); a caller requesting a materially longer-lived SAS would need this to size the
     * delegation key's own expiry around that request instead of this fixed window.
     */
    private UserDelegationKey currentDelegationKey() {
        CachedDelegationKey snapshot = cachedDelegationKey;
        OffsetDateTime now = OffsetDateTime.now();
        if (snapshot != null && now.isBefore(snapshot.expiry().minus(DELEGATION_KEY_REFRESH_MARGIN))) {
            return snapshot.key();
        }
        synchronized (this) {
            snapshot = cachedDelegationKey;
            now = OffsetDateTime.now();
            if (snapshot != null && now.isBefore(snapshot.expiry().minus(DELEGATION_KEY_REFRESH_MARGIN))) {
                return snapshot.key();
            }
            OffsetDateTime newExpiry = now.plus(DELEGATION_KEY_LIFETIME);
            UserDelegationKey key = serviceClient.getUserDelegationKey(now, newExpiry);
            cachedDelegationKey = new CachedDelegationKey(key, newExpiry);
            return key;
        }
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
