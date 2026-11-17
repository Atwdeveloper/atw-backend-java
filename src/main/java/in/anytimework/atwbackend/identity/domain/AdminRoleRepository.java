package in.anytimework.atwbackend.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID>, JpaSpecificationExecutor<AdminRole> {

    Optional<AdminRole> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID excludedId);
}
