package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerNoteRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerNoteResponse;
import in.anytimework.atwbackend.worker.domain.WorkerNote;
import in.anytimework.atwbackend.worker.domain.WorkerNoteRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Append-only admin remarks about a worker - covers the pre-seeded {@code workers.view_comments} permission. */
@Service
public class WorkerNoteService {

    private final WorkerRepository workers;
    private final WorkerNoteRepository notes;

    public WorkerNoteService(WorkerRepository workers, WorkerNoteRepository notes) {
        this.workers = workers;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerNoteResponse> list(UUID workerId, Pageable pageable) {
        requireWorkerExists(workerId);
        return PageResponse.from(notes.findByWorkerIdOrderByCreatedAtDesc(workerId, pageable).map(this::toDto));
    }

    @Transactional
    public WorkerNoteResponse create(UUID workerId, CreateWorkerNoteRequest request) {
        requireWorkerExists(workerId);
        return toDto(notes.save(new WorkerNote(workerId, request.note())));
    }

    private void requireWorkerExists(UUID workerId) {
        if (!workers.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
    }

    private WorkerNoteResponse toDto(WorkerNote note) {
        return new WorkerNoteResponse(note.getId(), note.getNote(), note.getCreatedBy(), note.getCreatedAt());
    }
}
