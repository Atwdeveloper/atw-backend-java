package in.anytimework.atwbackend.faceauth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WorkerFaceEnrollmentRepository extends JpaRepository<WorkerFaceEnrollment, UUID> {

    Optional<WorkerFaceEnrollment> findByWorkerId(UUID workerId);

    boolean existsByWorkerId(UUID workerId);

    /** Batch counterpart to {@link #findByWorkerId} - backs {@code FaceAuthService.getEnrollmentDates}. */
    List<WorkerFaceEnrollment> findByWorkerIdIn(Set<UUID> workerIds);
}
