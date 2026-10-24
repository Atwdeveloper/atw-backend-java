package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminAuthTokenRepository extends JpaRepository<AdminAuthToken, UUID> {

    Optional<AdminAuthToken> findByTokenHash(String tokenHash);

    /** Marks every still-active token of {@code type} for this admin as used (scoped by type so this never touches the separate INVITE lifecycle). */
    @Modifying
    @Query("update AdminAuthToken t set t.usedAt = :now where t.adminUserId = :adminId and t.type = :type and t.usedAt is null and t.expiresAt > :now")
    int invalidateActiveTokens(@Param("adminId") UUID adminId, @Param("type") AuthTokenType type, @Param("now") Instant now);
}
