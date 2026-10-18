package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.RefreshToken;
import in.anytimework.atwbackend.identity.domain.RefreshTokenRepository;
import in.anytimework.atwbackend.shared.security.OpaqueTokens;
import in.anytimework.atwbackend.shared.security.jwt.JwtProperties;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Issues, validates, rotates and revokes refresh tokens (stored only as SHA-256 hashes). */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties, Clock clock) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public record IssuedRefresh(String token, Instant expiresAt, UUID id) {
    }

    @Transactional
    public IssuedRefresh issue(UUID adminUserId, String ipAddress, String userAgent) {
        String token = OpaqueTokens.generate();
        RefreshToken entity = new RefreshToken(adminUserId, OpaqueTokens.sha256Hex(token),
                clock.instant().plus(jwtProperties.refreshTtl()), ipAddress, userAgent);
        repository.save(entity);
        return new IssuedRefresh(token, entity.getExpiresAt(), entity.getId());
    }

    @Transactional
    public RefreshToken validateActive(String token) {
        RefreshToken entity = repository.findByTokenHash(OpaqueTokens.sha256Hex(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!entity.isActive(clock.instant())) {
            throw new UnauthorizedException("Refresh token has expired or been revoked");
        }
        return entity;
    }

    @Transactional
    public IssuedRefresh rotate(RefreshToken current, String ipAddress, String userAgent) {
        IssuedRefresh next = issue(current.getAdminUserId(), ipAddress, userAgent);
        current.revoke(clock.instant());
        current.markReplacedBy(next.id());
        repository.save(current);
        return next;
    }

    @Transactional
    public Optional<UUID> revoke(String token) {
        return repository.findByTokenHash(OpaqueTokens.sha256Hex(token)).map(entity -> {
            entity.revoke(clock.instant());
            repository.save(entity);
            return entity.getAdminUserId();
        });
    }

    @Transactional
    public void revokeAllForAdmin(UUID adminUserId) {
        repository.revokeAllActiveForAdmin(adminUserId, clock.instant());
    }
}
