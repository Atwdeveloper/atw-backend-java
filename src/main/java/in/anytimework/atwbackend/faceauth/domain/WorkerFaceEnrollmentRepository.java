package in.anytimework.atwbackend.faceauth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WorkerFaceEnrollmentRepository extends JpaRepository<WorkerFaceEnrollment, UUID> {

    Optional<WorkerFaceEnrollment> findByWorkerId(UUID workerId);

    boolean existsByWorkerId(UUID workerId);

    /** Batch counterpart to {@link #findByWorkerId} - backs {@code FaceAuthService.getEnrollmentDates}. */
    List<WorkerFaceEnrollment> findByWorkerIdIn(Set<UUID> workerIds);

    /** The actual candidate pool for both {@code identify} and the enrollment-time duplicate check - every enrollment not excluded via {@code excludeFromMatching} (see that column's Javadoc on {@link WorkerFaceEnrollment}). */
    List<WorkerFaceEnrollment> findByExcludedFromMatchingAtIsNull();

    /** Plain id projection (never the embedding/quality columns) - every worker with a row here, regardless of exclusion status. Backs {@code FaceAuthService.findEnrolledWorkerIds}. */
    @Query("select e.workerId from WorkerFaceEnrollment e")
    Set<UUID> findAllWorkerIds();
}
