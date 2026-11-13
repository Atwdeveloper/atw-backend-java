package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * An append-only admin remark about a worker (e.g. "called to confirm availability"). Never updated
 * after creation - {@code BaseEntity}'s inherited {@code createdBy}/{@code createdAt} are exactly
 * "who wrote this and when", same as how {@code AdminAuditLog} relies on them rather than adding its
 * own duplicate actor/timestamp columns for ordinary (non-compliance-sensitive) attribution.
 */
@Getter
@Entity
@Table(name = "worker_note")
public class WorkerNote extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "note", nullable = false, columnDefinition = "text")
    private String note;

    protected WorkerNote() {
        // required by JPA
    }

    public WorkerNote(UUID workerId, String note) {
        this.workerId = workerId;
        this.note = note;
    }
}
