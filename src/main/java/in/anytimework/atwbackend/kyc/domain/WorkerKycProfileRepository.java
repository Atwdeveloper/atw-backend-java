package in.anytimework.atwbackend.kyc.domain;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WorkerKycProfileRepository extends JpaRepository<WorkerKycProfile, UUID> {

    Optional<WorkerKycProfile> findByWorkerId(UUID workerId);

    List<WorkerKycProfile> findByWorkerIdIn(Set<UUID> workerIds);

    boolean existsByAadhaarNumberHashAndWorkerIdNot(String aadhaarNumberHash, UUID workerId);

    boolean existsByAadhaarNumberHash(String aadhaarNumberHash);

    boolean existsByPanNumberHashAndWorkerIdNot(String panNumberHash, UUID workerId);

    /** Worker ids whose KYC is currently in any of the given aggregate states - backs the admin list's (multiselect) KYC-status filter. */
    @Query("select p.workerId from WorkerKycProfile p where p.overallStatus in :statuses")
    Set<UUID> findWorkerIdsByOverallStatus(@Param("statuses") Set<KycStatus> statuses);

    /**
     * Locks the row for update; serializes concurrent status recomputation for the same profile -
     * same pattern as {@code AdminUserRepository.findByIdForUpdate}/{@code WorkerRepository
     * .findByIdForUpdate}. Without this, two admins approving two different documents for the same
     * worker at the same moment can each read a snapshot that doesn't yet include the other's
     * (uncommitted) approval, both independently compute the same already-current overall_status, and
     * both saves become no-ops (Hibernate dirty-checks against what each one loaded) - silently
     * losing the transition to VERIFIED with no error raised to either admin. The lock forces the
     * second recompute to wait for the first to fully commit, so it always sees the complete picture.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WorkerKycProfile p where p.id = :id")
    Optional<WorkerKycProfile> findByIdForUpdate(@Param("id") UUID id);
}
