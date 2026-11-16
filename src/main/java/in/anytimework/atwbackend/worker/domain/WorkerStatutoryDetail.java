package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import in.anytimework.atwbackend.shared.security.WorkerEncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * A worker's statutory identity numbers - UAN (EPFO) and ESIC. Same shape as the {@code kyc} module's
 * {@code WorkerKycProfile} (Aadhaar/PAN): encrypted at rest with the worker-PII key, plus a separate
 * deterministic hash column per number for a duplicate check across workers, without ever decrypting
 * to search. Kept as its own satellite table in THIS module rather than folded into
 * {@code WorkerKycProfile}: UAN/ESIC are employment/statutory identifiers, not the kyc module's
 * identity-verification concern - no e-KYC provider verifies these, so there is no
 * {@code overallStatus}/verification workflow here, just declared values (matching how legacy itself
 * never hard-validated or verified either, only soft-flagged a missing one for an "incomplete
 * profile" admin filter).
 *
 * <p>Both numbers are masked/unmasked the same way as Aadhaar/PAN ({@code kyc.view_sensitive}) - same
 * risk category, government-issued identifiers - but this sub-resource is accessed/edited through
 * plain {@code workers.view}/{@code workers.edit} like {@link WorkerBankDetail}, not a dedicated
 * permission module of its own (unlike {@code kyc}/{@code faceauth}, which earned separate permission
 * namespaces because they also own a distinct verification workflow - UAN/ESIC here do not).</p>
 */
@Getter
@Entity
@Table(name = "worker_statutory_detail")
public class WorkerStatutoryDetail extends BaseEntity {

    @Column(name = "worker_id", nullable = false, unique = true)
    private UUID workerId;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "uan_number", length = 255)
    private String uanNumber;

    @Column(name = "uan_number_hash", length = 64)
    private String uanNumberHash;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "esic_number", length = 255)
    private String esicNumber;

    @Column(name = "esic_number_hash", length = 64)
    private String esicNumberHash;

    protected WorkerStatutoryDetail() {
        // required by JPA
    }

    public WorkerStatutoryDetail(UUID workerId) {
        this.workerId = workerId;
    }

    public void updateUan(String uanNumber, String uanNumberHash) {
        this.uanNumber = uanNumber;
        this.uanNumberHash = uanNumberHash;
    }

    public void updateEsic(String esicNumber, String esicNumberHash) {
        this.esicNumber = esicNumber;
        this.esicNumberHash = esicNumberHash;
    }
}
