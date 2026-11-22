package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WorkerDesignationRepository extends JpaRepository<WorkerDesignation, UUID> {

    List<WorkerDesignation> findByWorkerId(UUID workerId);

    /** Backs the admin worker list's "primary designation" column across a whole page in one query. */
    List<WorkerDesignation> findByWorkerIdInAndPrimaryTrue(Set<UUID> workerIds);

    void deleteByWorkerId(UUID workerId);
}
