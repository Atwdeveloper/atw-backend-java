package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkerNoteRepository extends JpaRepository<WorkerNote, UUID> {

    Page<WorkerNote> findByWorkerIdOrderByCreatedAtDesc(UUID workerId, Pageable pageable);

    /** Scopes a note lookup to a specific worker - deleting note A via worker B's path must 404, not silently delete a note that isn't B's. */
    Optional<WorkerNote> findByIdAndWorkerId(UUID id, UUID workerId);
}
