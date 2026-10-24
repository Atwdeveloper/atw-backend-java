package in.anytimework.atwbackend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpRequestsTest {

    @Test
    void noForwardedHeader_usesRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertThat(HttpRequests.clientIp(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void singleForwardedValue_isUsedAsIs() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");

        assertThat(HttpRequests.clientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void multipleForwardedValues_trustsOnlyTheLastHop_notTheAttackerControlledFirstOne() {
        // A client sending its own fabricated first entry, with the trusted proxy's real
        // observation appended after it - the classic X-Forwarded-For spoofing attempt.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 203.0.113.9");

        assertThat(HttpRequests.clientIp(request)).isEqualTo("203.0.113.9");
    }
}
