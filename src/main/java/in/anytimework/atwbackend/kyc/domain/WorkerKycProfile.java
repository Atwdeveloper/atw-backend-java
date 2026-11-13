package in.anytimework.atwbackend.kyc.domain;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import in.anytimework.atwbackend.shared.security.WorkerEncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A worker's declared identity numbers plus the outcome of verifying them. One row per worker
 * ({@code worker_id} is a real FK, {@code ON DELETE CASCADE}) - a deliberate correction from an
 * earlier draft of this design that modeled this generically (a polymorphic {@code owner_type}/
 * {@code owner_id} pair, so the same table could later serve company KYC too): that shape can never
 * carry a real foreign key, so the database could never guarantee a KYC record actually belongs to a
 * real worker. Reusability belongs in the {@code kyc} module's workflow, not a shared, integrity-free
 * table guessed at for a module that doesn't exist yet.
 *
 * <p>{@code aadhaarNumber}/{@code panNumber} are encrypted at rest with the worker-PII key (see
 * {@link WorkerEncryptedStringConverter}); {@code aadhaarNumberHash}/{@code panNumberHash} are each a
 * separate, deterministic HMAC-SHA256 of the normalized number used only for a duplicate check - the
 * encrypted value itself is never searchable.</p>
 *
 * <p><strong>{@code overallStatus} is set directly by whatever actually happened</strong> - manual
 * number entry moves it to PENDING (unverified, just recorded); a successful live Aadhaar e-KYC call
 * (see {@code WorkerIntakeService}) moves it straight to VERIFIED with {@code verificationRef}
 * recording the provider's reference id for audit/dispute purposes. There is no more "recompute from
 * several uploaded documents" step - an earlier version of this module required an admin to manually
 * review a photo of the Aadhaar card, which added no real trust once a government-backed API call
 * can confirm the same thing directly; that document-review workflow was removed.</p>
 */
@Getter
@Entity
@Table(name = "worker_kyc_profile")
public class WorkerKycProfile extends BaseEntity {

    @Column(name = "worker_id", nullable = false, unique = true)
    private UUID workerId;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "aadhaar_number", length = 255)
    private String aadhaarNumber;

    @Column(name = "aadhaar_number_hash", length = 64)
    private String aadhaarNumberHash;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "pan_number", length = 255)
    private String panNumber;

    @Column(name = "pan_number_hash", length = 64)
    private String panNumberHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 20)
    private KycStatus overallStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** The e-KYC provider's reference id for the verification call that produced VERIFIED - audit/dispute trail, not secret. */
    @Column(name = "verification_ref", length = 255)
    private String verificationRef;

    protected WorkerKycProfile() {
        // required by JPA
    }

    public WorkerKycProfile(UUID workerId) {
        this.workerId = workerId;
        this.overallStatus = KycStatus.NOT_STARTED;
    }

    /** Manually-entered, unverified Aadhaar number (the "manual add worker" path) - moves status to PENDING if not already further along. */
    public void updateAadhaar(String aadhaarNumber, String aadhaarNumberHash) {
        this.aadhaarNumber = aadhaarNumber;
        this.aadhaarNumberHash = aadhaarNumberHash;
        if (this.overallStatus == KycStatus.NOT_STARTED) {
            this.overallStatus = KycStatus.PENDING;
        }
    }

    public void updatePan(String panNumber, String panNumberHash) {
        this.panNumber = panNumber;
        this.panNumberHash = panNumberHash;
        if (this.overallStatus == KycStatus.NOT_STARTED) {
            this.overallStatus = KycStatus.PENDING;
        }
    }

    /** Set from a successful live Aadhaar e-KYC verification - see {@code WorkerIntakeServiceImpl}. */
    public void markVerifiedViaApi(String aadhaarNumber, String aadhaarNumberHash, String verificationRef, Instant verifiedAt) {
        this.aadhaarNumber = aadhaarNumber;
        this.aadhaarNumberHash = aadhaarNumberHash;
        this.verificationRef = verificationRef;
        this.verifiedAt = verifiedAt;
        this.overallStatus = KycStatus.VERIFIED;
    }
}
