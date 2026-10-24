package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserProfileRepository extends JpaRepository<AdminUserProfile, UUID> {

    Optional<AdminUserProfile> findByAdminUserId(UUID adminUserId);
}
