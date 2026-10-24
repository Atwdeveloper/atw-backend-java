package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AdminOtpRepository extends JpaRepository<AdminOtp, UUID> {

    /** Marks every still-active (unconsumed, unexpired) OTP challenge for this admin as consumed. */
    @Modifying
    @Query("update AdminOtp o set o.consumedAt = :now where o.adminUserId = :adminId and o.consumedAt is null and o.expiresAt > :now")
    int invalidateAllActiveForAdmin(@Param("adminId") UUID adminId, @Param("now") Instant now);
}
