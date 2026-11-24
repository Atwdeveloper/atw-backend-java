package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DesignationIndustryRepository extends JpaRepository<DesignationIndustry, UUID> {

    /** Single-resource path (get/create/update) - unordered, caller sorts for display. */
    List<DesignationIndustry> findByDesignationId(UUID designationId);

    /** Batched counterpart for a whole list page - one query, not one per row. */
    List<DesignationIndustry> findByDesignationIdIn(Set<UUID> designationIds);

    void deleteByDesignationId(UUID designationId);
}
