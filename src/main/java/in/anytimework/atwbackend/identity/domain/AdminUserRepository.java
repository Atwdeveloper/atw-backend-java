package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** Distinct permission codes granted to this admin via all its roles. */
    @Query("select distinct p.code from AdminUser a join a.roles r join r.permissions p where a.id = :id")
    List<String> findPermissionCodes(@Param("id") UUID id);

    /** Names of the roles assigned to this admin. */
    @Query("select r.name from AdminUser a join a.roles r where a.id = :id")
    List<String> findRoleNames(@Param("id") UUID id);
}
