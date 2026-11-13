package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, UUID>, JpaSpecificationExecutor<SkillCategory> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Used by the cross-module {@code SkillCategoryLookupService} (dropdowns want every category, unpaginated), not by the admin list endpoint. */
    List<SkillCategory> findAllByOrderByName();

    /** Atomic, gap-tolerant source for the human-facing skill-category code - see SkillCategoryService.generateSkillCategoryCode. */
    @Query(value = "SELECT nextval('skill_category_code_seq')", nativeQuery = true)
    long nextSkillCategoryCodeSequenceValue();
}
