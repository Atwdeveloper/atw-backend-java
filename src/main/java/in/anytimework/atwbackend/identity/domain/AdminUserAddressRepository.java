package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserAddressRepository extends JpaRepository<AdminUserAddress, UUID> {

    Optional<AdminUserAddress> findByAdminUserId(UUID adminUserId);
}
