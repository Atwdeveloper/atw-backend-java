package in.anytimework.atwbackend.company.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import in.anytimework.atwbackend.shared.security.CompanyEncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * A client company on the ATW platform: its profile, its GST/PAN declaration (and verification
 * state), and its approval lifecycle ({@link CompanyStatus}). Everything else - addresses, the
 * status-change audit trail, and the company's own login accounts - lives in its own satellite table
 * ({@link CompanyAddress}, {@link CompanyStatusHistory}, {@link CompanyUser}), mirroring how
 * {@code worker.domain.Worker} splits out {@code WorkerAddress}/{@code WorkerStatusHistory} and how
 * {@code identity.domain.AdminUser} splits out its own satellites.
 *
 * <p>{@code industryId} is a plain UUID into the masterdata module (validated via
 * {@code IndustryLookupService} at the service layer), never a JPA relationship - same reasoning as
 * {@code Worker.industryId}. Unlike worker's optional department/industry, this is {@code NOT NULL}:
 * a company's industry is how it gets matched to vacancies/workers later and must always be known.</p>
 *
 * <p>{@code gstNumber}/{@code panNumber} are encrypted at rest via {@link CompanyEncryptedStringConverter}
 * (its own, independently-keyed {@code companyPiiEncryptor} - see {@code PiiEncryptionConfig}) -
 * <strong>the {@code @Convert} below is the whole point; a converter class existing on its own
 * encrypts nothing</strong> (this exact mistake - a converter built but never applied to a field,
 * leaving it silently stored in plaintext - has happened three times before in this codebase's
 * history around {@code shared.kernel.Address}). {@code gstNumberHash}/{@code panNumberHash} are a
 * separate, deterministic one-way hash (never decrypted to search) used only for the duplicate-number
 * partial unique indexes in {@code V37__company.sql} - the encrypted column itself is never
 * searchable.</p>
 *
 * <p><strong>Soft delete:</strong> {@link #markDeleted} sets {@code deletedAt} rather than ever
 * issuing a real SQL DELETE - identical mechanism and reasoning to {@code Worker}/{@code AdminUser}.
 * {@code @SQLRestriction} rewrites every SQL statement Hibernate generates to load a {@code Company}
 * to add {@code AND deleted_at IS NULL}, uniformly and without exception.</p>
 *
 * <p><strong>Critical side effect, enforced by {@code CompanyService}, not here:</strong> moving this
 * company to {@link CompanyStatus#SUSPENDED}/{@link CompanyStatus#REJECTED}, or soft-deleting it,
 * must disable every one of its {@link CompanyUser} rows and revoke every one of their refresh
 * tokens, in the same transaction - a soft delete/status change never fires {@code ON DELETE CASCADE}
 * (only a real SQL DELETE would, and this codebase never does real deletes), so without this explicit
 * step a suspended/rejected/deleted company's owner could keep using an already-issued access token
 * until it naturally expired.</p>
 */
@Getter
@Entity
@Table(name = "company")
@SQLRestriction("deleted_at is null")
public class Company extends BaseEntity {

    @Column(name = "company_code", nullable = false, unique = true, length = 20)
    private String companyCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "industry_id", nullable = false)
    private UUID industryId;

    @Convert(converter = CompanyEncryptedStringConverter.class)
    @Column(name = "gst_number", length = 255)
    private String gstNumber;

    @Column(name = "gst_number_hash", length = 64)
    private String gstNumberHash;

    @Column(name = "gst_verified_at")
    private Instant gstVerifiedAt;

    /** Audit pointer to the vendor's verification response - mirrors {@code WorkerKycProfile.verificationRef}. */
    @Column(name = "gst_verification_ref", length = 255)
    private String gstVerificationRef;

    @Convert(converter = CompanyEncryptedStringConverter.class)
    @Column(name = "pan_number", length = 255)
    private String panNumber;

    @Column(name = "pan_number_hash", length = 64)
    private String panNumberHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CompanyStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Company() {
        // required by JPA
    }

    /**
     * Always starts {@link CompanyStatus#PENDING_VERIFICATION} - GST verification is always a
     * separate, later action ({@code CompanyService.triggerGstVerification}), never a precondition of
     * creation, so a vendor outage can never block onboarding a company. {@code gstNumber}/
     * {@code panNumber} (and their hashes) may be null - both are optional at creation.
     */
    public Company(String companyCode, String name, UUID industryId, String gstNumber, String gstNumberHash,
                   String panNumber, String panNumberHash) {
        this.companyCode = companyCode;
        this.name = name;
        this.industryId = industryId;
        this.gstNumber = gstNumber;
        this.gstNumberHash = gstNumberHash;
        this.panNumber = panNumber;
        this.panNumberHash = panNumberHash;
        this.status = CompanyStatus.PENDING_VERIFICATION;
    }

    /** Name/industry only - GST/PAN have their own verification lifecycle and are never edited here, and status goes through {@link #changeStatus}. */
    public void updateProfile(String name, UUID industryId) {
        this.name = name;
        this.industryId = industryId;
    }

    /** Set from a successful {@code GstVerificationPort.verify} call - see {@code CompanyService.triggerGstVerification}. */
    public void markGstVerified(String verificationRef, Instant verifiedAt) {
        this.gstVerificationRef = verificationRef;
        this.gstVerifiedAt = verifiedAt;
    }

    /** Explicit target status + optional reason - never a blind toggle. See {@code CompanyService} for the enforced state-machine rules. */
    public void changeStatus(CompanyStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
    }

    /** Soft-deletes this company - see the class-level Javadoc for what this makes true from here on, and for the side effect {@code CompanyService.delete} applies alongside it. */
    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }
}
