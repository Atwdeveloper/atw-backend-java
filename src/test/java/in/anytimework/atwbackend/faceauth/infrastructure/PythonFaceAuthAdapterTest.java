package in.anytimework.atwbackend.faceauth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.anytimework.atwbackend.faceauth.application.FaceEmbeddingResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for the one piece of this feature with a genuinely unverified external contract (see the
 * adapter's own Javadoc) - exercises parsing against the real service's actual {@code app/main.py}
 * response shapes (plain 200 on success, real HTTP error codes with a structured {@code detail} on
 * failure), not the earlier, wrong "always 200, check a status field" shape this adapter used to
 * assume.
 */
class PythonFaceAuthAdapterTest {

    // No .requestFactory(...) call anywhere in this chain - that would silently discard the mock
    // request factory MockRestServiceServer.bindTo(...) installs on the builder. See
    // PythonFaceAuthAdapter's constructor Javadoc for why the adapter itself never touches this.
    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://face-auth.test")
            .defaultHeader("X-API-Key", "test-key");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PythonFaceAuthAdapter adapter = new PythonFaceAuthAdapter(builder.build(), new ObjectMapper());

    @Test
    void embed_onSuccess_parsesEmbeddingQualityAndLiveness() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess("""
                        {"embedding":[0.1,0.2,0.3],"model":"sface","dim":3,
                         "quality":{"face_width_px":410,"sharpness":0.8,"brightness":0.6,"is_acceptable":true,"reasons":[]},
                         "liveness":{"is_live":true,"confidence":0.97,"scores":[0.01,0.97,0.02]}}
                        """, MediaType.APPLICATION_JSON));

        FaceEmbeddingResult result = adapter.embed(new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(result.embedding()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.quality().faceWidthPx()).isEqualTo(410);
        assertThat(result.quality().acceptable()).isTrue();
        assertThat(result.quality().reasons()).isEmpty();
        assertThat(result.live()).isTrue();
        assertThat(result.livenessConfidence()).isEqualTo(0.97);
        server.verify();
    }

    @Test
    void embed_whenNoFaceDetected_throwsWithTheRealErrorCode() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":{\"error\":\"no_face_detected\"}}"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no_face_detected");
    }

    @Test
    void embed_whenMultipleFacesDetected_includesTheCountInTheMessage() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":{\"error\":\"multiple_faces_detected\",\"count\":2}}"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multiple_faces_detected")
                .hasMessageContaining("2");
    }

    /** Shape confirmed live against the real Render deployment on 2026-11-17 - this exact body, byte for byte. */
    @Test
    void embed_whenImageCannotBeDecoded_includesTheReasonInTheMessage() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":{\"error\":\"invalid_image\",\"reason\":\"Could not decode image\"}}"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invalid_image")
                .hasMessageContaining("Could not decode image");
    }

    @Test
    void embed_whenInvalidApiKey_throwsWithTheRealErrorCode() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":{\"error\":\"invalid_api_key\"}}"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invalid_api_key");
    }

    @Test
    void embed_whenDetailIsABareString_usesThatMessageDefensively() {
        // The real service's own docstring says detail is always a structured object, never a bare
        // string - this defends against a reverse proxy or load balancer answering instead, same
        // defensive handling legacy's PHP client has.
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Image too dark\"}"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Image too dark");
    }

    @Test
    void embed_whenErrorBodyIsNotJson_fallsBackToStatusText() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>502 Bad Gateway</html>"));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Bad Gateway");
    }

    @Test
    void embed_whenQualityIsUnacceptable_stillReturnsNormallyForTheServiceLayerToDecide() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withSuccess("""
                        {"embedding":[0.1,0.2],"model":"sface","dim":2,
                         "quality":{"face_width_px":80,"sharpness":0.1,"brightness":0.2,"is_acceptable":false,"reasons":["too_blurry","face_too_small"]},
                         "liveness":{"is_live":false,"confidence":0.3,"scores":[0.6,0.3,0.1]}}
                        """, MediaType.APPLICATION_JSON));

        FaceEmbeddingResult result = adapter.embed(new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(result.quality().acceptable()).isFalse();
        assertThat(result.quality().reasons()).containsExactly("too_blurry", "face_too_small");
        assertThat(result.live()).isFalse();
    }

    @Test
    void embed_onConnectionFailure_throwsABusinessRuleException() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
