package in.anytimework.atwbackend.document;

import in.anytimework.atwbackend.document.application.FileStorage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link FileStorage} test double - no Azurite/Azure connection needed to test upload/download flows. */
public class FakeFileStorage implements FileStorage {

    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();

    @Override
    public void upload(String key, byte[] data, String contentType) {
        blobs.put(key, data);
    }

    @Override
    public byte[] download(String key) {
        byte[] data = blobs.get(key);
        if (data == null) {
            throw new IllegalStateException("No such key: " + key);
        }
        return data;
    }

    @Override
    public URI generateDownloadUrl(String key, Duration validFor) {
        return URI.create("fake://storage/" + key);
    }

    @Override
    public void delete(String key) {
        blobs.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return blobs.containsKey(key);
    }
}
