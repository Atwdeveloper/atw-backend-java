package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Used by the cross-module {@code DepartmentLookupService} (dropdowns want every department, unpaginated), not by the admin list endpoint. */
    List<Department> findAllByOrderByName();
}
