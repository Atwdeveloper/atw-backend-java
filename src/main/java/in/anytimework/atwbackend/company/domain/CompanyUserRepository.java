package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    /** Only ever true for a non-deleted company user - {@link CompanyUser}'s {@code @SQLRestriction} applies here too. */
    boolean existsByEmailIgnoreCase(String email);

    List<CompanyUser> findByCompanyId(UUID companyId);

    /** Id-only projection (no entity loaded into the persistence context) - used by CompanyService's status-change cascade right before a bulk update on this same table/company_refresh_token, so a stale managed entity is never read back afterward in the same transaction. */
    @Query("select u.id from CompanyUser u where u.companyId = :companyId")
    List<UUID> findIdsByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Sets every one of this company's users to {@code status} in one statement - the cascade
     * {@code CompanyService} fires on a company status change into/out of ACTIVE (see
     * {@link Company}'s Javadoc). Guarded by {@code status <> :status} so a no-op case touches no
     * rows. Same bulk-JPQL-update convention as {@code identity.domain.AdminRefreshTokenRepository.revokeAllActiveForAdmin}.
     */
    @Modifying
    @Query("update CompanyUser u set u.status = :status where u.companyId = :companyId and u.status <> :status")
    int updateStatusForCompany(@Param("companyId") UUID companyId, @Param("status") CompanyUserStatus status);
}
