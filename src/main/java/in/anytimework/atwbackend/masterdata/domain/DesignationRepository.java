package in.anytimework.atwbackend.masterdata.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID>, JpaSpecificationExecutor<Designation> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Used by the cross-module {@code DesignationLookupService} (dropdowns want every designation, unpaginated), not by the admin list endpoint. */
    List<Designation> findAllByOrderByName();

    /**
     * Locks the row for update; serializes a concurrent full industry-replace of this designation
     * against another one for the same designation - same pattern as
     * {@code WorkerRepository.findByIdForUpdate}. The {@code uq_designation_industry_primary} partial
     * unique index (added alongside {@link DesignationIndustry}) makes a race between two concurrent
     * replaces newly able to throw a DB constraint violation where none existed before; this lock
     * prevents the race itself rather than relying on catching that exception after the fact.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Designation d where d.id = :id")
    Optional<Designation> findByIdForUpdate(@Param("id") UUID id);
}
