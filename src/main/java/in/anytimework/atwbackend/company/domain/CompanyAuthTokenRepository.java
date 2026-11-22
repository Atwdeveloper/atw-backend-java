package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Mirrors {@code identity.domain.AdminAuthTokenRepository} exactly. */
public interface CompanyAuthTokenRepository extends JpaRepository<CompanyAuthToken, UUID> {

    Optional<CompanyAuthToken> findByTokenHash(String tokenHash);

    /** Marks every still-active token of {@code type} for this company user as used (scoped by type so this never touches the separate INVITE/PASSWORD_RESET lifecycles). */
    @Modifying
    @Query("update CompanyAuthToken t set t.usedAt = :now where t.companyUserId = :companyUserId and t.type = :type and t.usedAt is null and t.expiresAt > :now")
    int invalidateActiveTokens(@Param("companyUserId") UUID companyUserId, @Param("type") CompanyAuthTokenType type,
                               @Param("now") Instant now);
}
