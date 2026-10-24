package in.anytimework.atwbackend.shared.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@link JwtConfig#jwtDecoder} issuer check: a token whose {@code iss} claim
 * matches {@link JwtProperties#issuer()} decodes normally, while one issued by any other
 * party is rejected even though its signature and timestamps are otherwise valid.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtConfigIssuerTest {

    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtProperties properties;

    private String encodeWithIssuer(String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void tokenWithConfiguredIssuerDecodesSuccessfully() {
        String token = encodeWithIssuer(properties.issuer());

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo(properties.issuer());
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        String token = encodeWithIssuer("someone-else");

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }
}
