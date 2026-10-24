package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserBankDetailRepository extends JpaRepository<AdminUserBankDetail, UUID> {

    Optional<AdminUserBankDetail> findByAdminUserId(UUID adminUserId);
}
