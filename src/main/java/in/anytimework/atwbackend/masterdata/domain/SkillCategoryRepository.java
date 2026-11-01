package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<SkillCategory> findAllByOrderByName();
}
