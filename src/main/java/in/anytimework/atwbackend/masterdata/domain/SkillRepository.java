package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {

    boolean existsByDesignationIdAndName(UUID designationId, String name);

    boolean existsByDesignationIdAndNameAndIdNot(UUID designationId, String name, UUID id);

    /** Used by the cross-module {@code SkillLookupService} (dropdowns want every skill, unpaginated), not by the admin list endpoint. */
    List<Skill> findAllByOrderByName();

    /** Used by {@code SkillLookupService.listByDesignation} (and worker's per-designation skill dropdown), not by the admin list endpoint. */
    List<Skill> findByDesignationIdOrderByName(UUID designationId);
}
