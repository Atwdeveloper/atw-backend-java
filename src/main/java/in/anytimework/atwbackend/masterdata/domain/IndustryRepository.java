package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IndustryRepository extends JpaRepository<Industry, UUID>, JpaSpecificationExecutor<Industry> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Used by the cross-module {@code IndustryLookupService} (dropdowns want every industry, unpaginated), not by the admin list endpoint. */
    List<Industry> findAllByOrderByName();

    /** Atomic, gap-tolerant source for the human-facing industry code - see IndustryService.generateIndustryCode. */
    @Query(value = "SELECT nextval('industry_code_seq')", nativeQuery = true)
    long nextIndustryCodeSequenceValue();
}
