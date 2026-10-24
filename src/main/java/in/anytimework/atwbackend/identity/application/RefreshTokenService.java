package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminRefreshToken;
import in.anytimework.atwbackend.identity.domain.AdminRefreshTokenRepository;
import in.anytimework.atwbackend.shared.security.OpaqueTokens;
import in.anytimework.atwbackend.shared.security.jwt.JwtProperties;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, validates, rotates and revokes refresh tokens (stored only as SHA-256 hashes).
 *
 * <p>Every token belongs to a rotation "family" (unchanged across rotations, starting at the
 * token's own id when first issued at login). Replaying a token that was already rotated away -
 * i.e. revoked specifically because it was replaced, not because of an explicit logout/disable -
 * is a strong signal of token theft (RFC 6819 §5.2.2.3): {@link #validateActive} responds by
 * revoking the whole family, cutting off whichever side (attacker or legitimate user) currently
 * holds the live descendant, and forcing a fresh login.</p>
 */
@Service
public class RefreshTokenService {

    private final AdminRefreshTokenRepository repository;
    private final RefreshTokenReuseHandler reuseHandler;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenService(AdminRefreshTokenRepository repository, RefreshTokenReuseHandler reuseHandler,
                               JwtProperties jwtProperties, Clock clock) {
        this.repository = repository;
        this.reuseHandler = reuseHandler;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public record IssuedRefresh(String token, Instant expiresAt, UUID id) {
    }

    /** Issues the first token of a brand-new family (a fresh login). */
    @Transactional
    public IssuedRefresh issue(UUID adminUserId, String ipAddress, String userAgent) {
        return issue(adminUserId, UUID.randomUUID(), ipAddress, userAgent);
    }

    @Transactional
    public AdminRefreshToken validateActive(String token, String ip, String userAgent) {
        AdminRefreshToken entity = repository.findByTokenHash(OpaqueTokens.sha256Hex(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!entity.isActive(clock.instant())) {
            if (entity.getRevokedAt() != null && entity.getReplacedById() != null) {
                // Revoked specifically because it was rotated away (not an explicit logout/disable),
                // and it's being presented again - treat as theft, not as an ordinary stale token.
                // Handled by a separate bean/transaction - see RefreshTokenReuseHandler's Javadoc
                // for why this must not be inlined here.
                reuseHandler.handle(entity.getFamilyId(), entity.getAdminUserId(), ip, userAgent);
                throw new UnauthorizedException("This session was revoked for your safety. Please log in again.");
            }
            throw new UnauthorizedException("Refresh token has expired or been revoked");
        }
        return entity;
    }

    @Transactional
    public IssuedRefresh rotate(AdminRefreshToken current, String ipAddress, String userAgent) {
        IssuedRefresh next = issue(current.getAdminUserId(), current.getFamilyId(), ipAddress, userAgent);
        current.revoke(clock.instant());
        current.markReplacedBy(next.id());
        repository.save(current);
        return next;
    }

    /**
     * REQUIRES_NEW so this always commits on its own, independent of any caller's transaction. In
     * particular, {@code AuthService.refresh()} calls this from inside its own now-@Transactional
     * method when an account turns out to be disabled, immediately followed by throwing — without
     * REQUIRES_NEW, that enclosing transaction's rollback would silently undo this revoke too.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    private IssuedRefresh issue(UUID adminUserId, UUID familyId, String ipAddress, String userAgent) {
        String token = OpaqueTokens.generate();
        AdminRefreshToken entity = new AdminRefreshToken(adminUserId, familyId, OpaqueTokens.sha256Hex(token),
                clock.instant().plus(jwtProperties.refreshTtl()), ipAddress, userAgent);
        repository.save(entity);
        return new IssuedRefresh(token, entity.getExpiresAt(), entity.getId());
    }
}
