package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A worker's core platform profile: identity, contact, and employment classification. Everything
 * sensitive enough to need its own permission boundary and/or encryption at rest lives in a separate
 * satellite table - {@link WorkerAddress}, {@link WorkerBankDetail} here, plus the {@code kyc}
 * module's {@code worker_kyc_profile}/{@code worker_kyc_document} - mirroring how the identity
 * module splits {@code AdminUser} from {@code AdminUserAddress}/{@code AdminUserKyc}/
 * {@code AdminUserBankDetail}.
 *
 * <p>{@code departmentId}/{@code industryId} are plain UUIDs into the masterdata module, validated
 * via its lookup services, never a JPA relationship - a designation/skill assignment is a genuine
 * many-valued relationship (see {@link WorkerDesignation}/{@link WorkerSkill}) so it gets its own
 * table instead of a column here.</p>
 *
 * <p><strong>Soft delete:</strong> {@link #markDeleted} sets {@code deletedAt} rather than the row
 * ever being physically removed - see {@code WorkerService.delete()}'s Javadoc for the full business
 * reasoning. {@code @SQLRestriction} is Hibernate's own documented mechanism for exactly this (not a
 * custom framework): it rewrites <em>every</em> SQL statement Hibernate generates to load a
 * {@code Worker} - {@code findById}, a {@code Specification}-based list, the pessimistic-lock JPQL in
 * {@code WorkerRepository.findByIdForUpdate}, {@code existsByMobileNumber(AndIdNot)} - to add
 * {@code AND deleted_at IS NULL}, uniformly and without exception (the annotation's own Javadoc: "always
 * applied and cannot be disabled"). A deleted worker therefore simply stops being visible anywhere in
 * this codebase once {@link #markDeleted} is saved, with no other method in {@code WorkerService} (or
 * anywhere else) needing to check a "deleted" flag itself.</p>
 */
@Getter
@Entity
@Table(name = "worker")
@SQLRestriction("deleted_at is null")
public class Worker extends BaseEntity {

    @Column(name = "worker_code", nullable = false, unique = true, length = 20)
    private String workerCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Optional. Raw as declared/captured - e.g. via the Aadhaar e-KYC wizard's "care of" value, which
     * UIDAI often prefixes ({@code "S/O: Ramesh Kumar"}) rather than returning a bare name; this field
     * stores whatever was given/captured without attempting to strip such a prefix (see
     * {@code AadhaarVerifiedDetails}' Javadoc for why).
     */
    @Column(name = "father_name", length = 255)
    private String fatherName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Unique only among non-deleted workers - a partial index, not {@code unique = true} here; see V27__worker_soft_delete.sql. */
    @Column(name = "mobile_number", nullable = false, length = 20)
    private String mobileNumber;

    @Column(name = "personal_email", length = 191)
    private String personalEmail;

    /** Optional - not every worker is classified into a department (see Worker's class Javadoc history). */
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "industry_id")
    private UUID industryId;

    /** Optional, self-reported years of prior work experience - a plain count, no implied employer history. */
    @Column(name = "work_experience_years")
    private Integer workExperienceYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "profile_photo_document_id")
    private UUID profilePhotoDocumentId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Worker() {
        // required by JPA
    }

    public Worker(String workerCode, String firstName, String middleName, String lastName, String fatherName,
                 Gender gender, LocalDate dateOfBirth, String mobileNumber, String personalEmail,
                 UUID departmentId, UUID industryId, Integer workExperienceYears) {
        this.workerCode = workerCode;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.fatherName = fatherName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.mobileNumber = mobileNumber;
        this.personalEmail = personalEmail;
        this.departmentId = departmentId;
        this.industryId = industryId;
        this.workExperienceYears = workExperienceYears;
        this.status = WorkerStatus.REGISTERED;
    }

    public void updateProfile(String firstName, String middleName, String lastName, String fatherName, Gender gender,
                              LocalDate dateOfBirth, String mobileNumber, String personalEmail,
                              UUID departmentId, UUID industryId, Integer workExperienceYears) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.fatherName = fatherName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.mobileNumber = mobileNumber;
        this.personalEmail = personalEmail;
        this.departmentId = departmentId;
        this.industryId = industryId;
        this.workExperienceYears = workExperienceYears;
    }

    /** Explicit target status + optional reason - never a blind toggle. See WorkerService for transition rules. */
    public void changeStatus(WorkerStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
    }

    public void setProfilePhotoDocumentId(UUID profilePhotoDocumentId) {
        this.profilePhotoDocumentId = profilePhotoDocumentId;
    }

    /** Soft-deletes this worker - see the class-level Javadoc on {@code @SQLRestriction} for what this makes true from here on. */
    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }
}
