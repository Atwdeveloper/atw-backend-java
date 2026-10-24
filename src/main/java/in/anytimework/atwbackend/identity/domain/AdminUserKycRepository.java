package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserKycRepository extends JpaRepository<AdminUserKyc, UUID> {

    Optional<AdminUserKyc> findByAdminUserId(UUID adminUserId);
}
