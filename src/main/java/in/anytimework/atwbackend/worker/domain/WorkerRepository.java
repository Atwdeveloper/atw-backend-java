package in.anytimework.atwbackend.worker.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkerRepository extends JpaRepository<Worker, UUID>, JpaSpecificationExecutor<Worker> {

    /** Only ever true for a non-deleted worker - {@link Worker}'s {@code @SQLRestriction} applies here too, so a deleted worker's old mobile number reads back as available. */
    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByMobileNumberAndIdNot(String mobileNumber, UUID id);

    /** Atomic, gap-tolerant source for the human-facing worker_code - see WorkerService.generateWorkerCode. */
    @Query(value = "SELECT nextval('worker_code_seq')", nativeQuery = true)
    long nextWorkerCodeSequenceValue();

    /**
     * Locks the row for update; serializes a concurrent full-replace of this worker's designations
     * or skills against another one for the same worker - same pattern as
     * {@code AdminUserRepository.findByIdForUpdate}. Without this, two near-simultaneous
     * delete-then-insert replacements for the same worker can silently lost-update each other
     * (neither write conflicts with a unique constraint if the two new sets don't overlap, so nothing
     * surfaces as an error - the second writer's result just silently wins).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worker w where w.id = :id")
    Optional<Worker> findByIdForUpdate(@Param("id") UUID id);
}
