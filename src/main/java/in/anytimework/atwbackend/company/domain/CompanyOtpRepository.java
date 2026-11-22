package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Mirrors {@code identity.domain.AdminOtpRepository} exactly. */
public interface CompanyOtpRepository extends JpaRepository<CompanyOtp, UUID> {

    /** Marks every still-active (unconsumed, unexpired) OTP challenge for this company user as consumed. */
    @Modifying
    @Query("update CompanyOtp o set o.consumedAt = :now where o.companyUserId = :companyUserId and o.consumedAt is null and o.expiresAt > :now")
    int invalidateAllActiveForUser(@Param("companyUserId") UUID companyUserId, @Param("now") Instant now);
}
