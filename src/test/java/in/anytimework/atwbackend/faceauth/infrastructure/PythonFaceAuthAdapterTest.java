package in.anytimework.atwbackend.faceauth.infrastructure;

import in.anytimework.atwbackend.faceauth.application.FaceEmbeddingResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for the one piece of this feature with a genuinely unverified external contract (see the
 * adapter's own Javadoc) - exercises the "always HTTP 200, check the status field" parsing logic
 * specifically, since a transcription mistake there would silently misreport every enrollment.
 */
class PythonFaceAuthAdapterTest {

    // No .requestFactory(...) call anywhere in this chain - that would silently discard the mock
    // request factory MockRestServiceServer.bindTo(...) installs on the builder. See
    // PythonFaceAuthAdapter's constructor Javadoc for why the adapter itself never touches this.
    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://face-auth.test")
            .defaultHeader("X-API-Key", "test-key");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PythonFaceAuthAdapter adapter = new PythonFaceAuthAdapter(builder.build());

    @Test
    void embed_onSuccess_parsesEmbeddingQualityAndLiveness() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess("""
                        {"status":"success","embedding":[0.1,0.2,0.3],
                         "quality":{"face_width_px":410,"sharpness":0.8,"brightness":0.6,"is_acceptable":true,"reasons":[]},
                         "liveness":{"is_live":true,"confidence":0.97}}
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
    void embed_whenStatusFieldIsErrorWithStructuredDetail_throwsDespiteHttp200() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withSuccess("""
                        {"status":"error","detail":{"error":"no_face_detected","message":"no face in image"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no_face_detected");
    }

    @Test
    void embed_whenStatusFieldIsErrorWithPlainStringDetail_usesThatMessage() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withSuccess("{\"status\":\"error\",\"detail\":\"Image too dark\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.embed(new byte[] {1, 2, 3}, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Image too dark");
    }

    @Test
    void embed_whenQualityIsUnacceptable_stillReturnsNormallyForTheServiceLayerToDecide() {
        server.expect(requestTo("http://face-auth.test/v1/embed"))
                .andRespond(withSuccess("""
                        {"status":"success","embedding":[0.1,0.2],
                         "quality":{"face_width_px":80,"sharpness":0.1,"brightness":0.2,"is_acceptable":false,"reasons":["too_blurry","face_too_small"]},
                         "liveness":{"is_live":false,"confidence":0.3}}
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
