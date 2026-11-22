package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Mirrors {@code identity.domain.AdminRefreshTokenRepository} exactly, plus a multi-user bulk revoke for the company-level cascade. */
public interface CompanyRefreshTokenRepository extends JpaRepository<CompanyRefreshToken, UUID> {

    Optional<CompanyRefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every still-active refresh token for one company user (used on password change/reset). */
    @Modifying
    @Query("update CompanyRefreshToken t set t.revokedAt = :now where t.companyUserId = :companyUserId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("companyUserId") UUID companyUserId, @Param("now") Instant now);

    /**
     * Revoke every still-active refresh token belonging to ANY of the given company users in one
     * statement - the cascade {@code CompanyService} fires when a company moves to
     * SUSPENDED/REJECTED or is soft-deleted (see {@link Company}'s Javadoc). Callers must never pass
     * an empty collection (an empty SQL {@code IN ()} is invalid) - guard before calling, same rule
     * as every other cross-module id-list filter in this codebase.
     */
    @Modifying
    @Query("update CompanyRefreshToken t set t.revokedAt = :now where t.companyUserId in :companyUserIds and t.revokedAt is null")
    int revokeActiveForUsers(@Param("companyUserIds") Collection<UUID> companyUserIds, @Param("now") Instant now);

    /** Revoke every still-active token in a rotation family (used on detected reuse of a dead token). */
    @Modifying
    @Query("update CompanyRefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamilyActive(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
