package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID>, JpaSpecificationExecutor<Designation> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Used by the cross-module {@code DesignationLookupService} (dropdowns want every designation, unpaginated), not by the admin list endpoint. */
    List<Designation> findAllByOrderByName();
}
