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

/**
 * Admin remarks about a worker - never editable after creation, but deletable (hard delete, see
 * {@link #delete}) by a holder of the dedicated {@code workers.delete_comments} permission. Reading
 * and creating are covered by the pre-seeded {@code workers.view_comments}/{@code workers.edit}.
 */
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

    /**
     * Hard delete - the row is permanently removed, no recovery (a deliberate choice for this
     * feature, unlike {@code Worker}'s own soft-delete - see {@code AdminWorkerController.deleteNote}'s
     * Javadoc). Throws {@link ResourceNotFoundException} if the note doesn't exist, or exists but
     * belongs to a different worker than {@code workerId} names.
     */
    @Transactional
    public void delete(UUID workerId, UUID noteId) {
        WorkerNote note = notes.findByIdAndWorkerId(noteId, workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        notes.delete(note);
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
