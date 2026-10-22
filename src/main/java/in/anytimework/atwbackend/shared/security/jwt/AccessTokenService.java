package in.anytimework.atwbackend.shared.security.jwt;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Issues short-lived HS256 access tokens from {@link AdminTokenClaims}. */
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(AdminTokenClaims claims) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTtl());
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(claims.adminId().toString())
                .claim("username", claims.username())
                .claim("email", claims.email())
                .claim("name", claims.fullName())
                .claim("super_admin", claims.superAdmin())
                .claim("authorities", claims.authorities())
                .claim("roles", claims.roles())
                .claim("sv", claims.securityVersion())
                .claim("must_change_password", claims.mustChangePassword())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt, properties.accessTtl().toSeconds());
    }

    /** A signed access token plus its expiry metadata (for the API response). */
    public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
