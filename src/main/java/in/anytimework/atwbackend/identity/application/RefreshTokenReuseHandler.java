package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminRefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Handles a detected refresh-token reuse (see {@link RefreshTokenService#validateActive}) as its
 * own, separate bean. That's not incidental: {@code validateActive} throws an
 * {@link in.anytimework.atwbackend.shared.exception.UnauthorizedException} right after triggering
 * this, and that's an unchecked exception propagating out of an {@code @Transactional} call chain -
 * Spring's default rule marks the whole transaction rollback-only, which would silently undo the
 * family revocation and audit record along with it (the same pitfall
 * {@code RefreshTokenService.revoke}'s Javadoc and {@code OtpService.attemptVerify}'s already
 * guard against). {@code REQUIRES_NEW} only actually opens a new transaction when the call crosses
 * a Spring proxy boundary - a same-class self-invocation (calling a sibling method on {@code this})
 * would silently ignore it, so this has to live on a different bean, not a private method on
 * {@code RefreshTokenService}.
 */
@Service
public class RefreshTokenReuseHandler {

    private final AdminRefreshTokenRepository repository;
    private final AdminAuditService audit;
    private final Clock clock;

    public RefreshTokenReuseHandler(AdminRefreshTokenRepository repository, AdminAuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID familyId, UUID adminUserId, String ip, String userAgent) {
        repository.revokeFamilyActive(familyId, clock.instant());
        audit.record(adminUserId, "REFRESH_TOKEN_REUSE_DETECTED",
                "Replayed an already-rotated refresh token; entire session family revoked", ip, userAgent);
    }
}
