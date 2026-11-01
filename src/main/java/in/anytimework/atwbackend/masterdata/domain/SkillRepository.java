package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    boolean existsByDesignationIdAndName(UUID designationId, String name);

    boolean existsByDesignationIdAndNameAndIdNot(UUID designationId, String name, UUID id);

    List<Skill> findAllByOrderByName();

    List<Skill> findByDesignationIdOrderByName(UUID designationId);
}
