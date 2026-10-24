package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, UUID> {

    Optional<AdminPermission> findByCode(String code);

    List<AdminPermission> findAllByOrderByModuleAscActionAsc();

    List<AdminPermission> findAllByCodeIn(Collection<String> codes);
}
