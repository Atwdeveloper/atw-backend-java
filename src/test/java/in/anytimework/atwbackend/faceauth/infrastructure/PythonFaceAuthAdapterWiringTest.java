package in.anytimework.atwbackend.faceauth.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real incident (2026-11-18): the deployed dev container crash-looped on
 * startup with "Parameter 1 of constructor in PythonFaceAuthAdapter required a bean of type
 * 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found" - the adapter was still
 * importing Jackson 2's {@code ObjectMapper}/{@code JsonNode} after this project moved to Jackson 3
 * ({@code tools.jackson.databind}), which is what Spring Boot 4's {@code JacksonAutoConfiguration}
 * actually provides a bean of (see every other {@code ObjectMapper}-injecting class in
 * {@code shared.security}). Every other test runs under the {@code stub} provider (see
 * {@code StubFaceAuthAdapter}), which never constructs this class at all - the whole suite stayed
 * green while production crashed. This boots the real context with the {@code python} provider
 * active specifically to close that blind spot.
 */
@SpringBootTest(properties = {
        "atw.face-auth.provider=python",
        "atw.face-auth.base-url=http://localhost:1",
        "atw.face-auth.api-key=test-key"
})
@ActiveProfiles("test")
class PythonFaceAuthAdapterWiringTest {

    @Autowired
    private PythonFaceAuthAdapter adapter;

    @Test
    void contextLoadsWithThePythonProviderActive() {
        assertThat(adapter).isNotNull();
    }
}
