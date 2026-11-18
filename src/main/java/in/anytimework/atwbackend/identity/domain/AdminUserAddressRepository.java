package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AdminUserAddressRepository extends JpaRepository<AdminUserAddress, UUID> {

    Optional<AdminUserAddress> findByAdminUserId(UUID adminUserId);

    /** Batch load for a page of staff - one query, never per row. See {@code AdminStaffService.addressNamesByStaff}. */
    List<AdminUserAddress> findByAdminUserIdIn(Set<UUID> adminUserIds);
}
