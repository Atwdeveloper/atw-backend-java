package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every still-active refresh token for an admin (used on password change / reset). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.adminUserId = :adminId and t.revokedAt is null")
    int revokeAllActiveForAdmin(@Param("adminId") UUID adminId, @Param("now") Instant now);
}
