package in.anytimework.atwbackend.document;

import in.anytimework.atwbackend.document.application.FileStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Swaps in {@link FakeFileStorage} so tests exercising the document module don't need Azurite running. */
@TestConfiguration
public class FileStorageTestConfig {

    @Bean
    @Primary
    public FileStorage fileStorage() {
        return new FakeFileStorage();
    }
}
