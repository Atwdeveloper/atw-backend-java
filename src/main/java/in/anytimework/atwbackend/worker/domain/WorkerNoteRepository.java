package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkerNoteRepository extends JpaRepository<WorkerNote, UUID> {

    Page<WorkerNote> findByWorkerIdOrderByCreatedAtDesc(UUID workerId, Pageable pageable);
}
