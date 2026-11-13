package in.anytimework.atwbackend.kyc.domain;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Aadhaar e-KYC verification attempt - the live, government-backed replacement for the manual
 * "upload a photo, admin reviews it" workflow this module started with. Independent of any worker
 * (none may exist yet - a new worker is typically created *from* a successful verification, via
 * {@code WorkerIntakeService}), so it is not FK'd to {@code worker}; {@link #consumedByWorkerId} is
 * populated after the fact, purely for traceability.
 *
 * <p>Verified PII (name/DOB/gender/address) is captured here only long enough for an admin to review
 * and submit the worker-creation form - this is staging data for a few minutes' round trip, not a
 * permanent record of the same rigor as {@code WorkerKycProfile}. Address lines and the Aadhaar
 * number itself are still encrypted with the worker-PII key, since they're genuine PII regardless of
 * how briefly they're expected to live here.</p>
 */
@Getter
@Entity
@Table(name = "worker_aadhaar_verification")
public class WorkerAadhaarVerification extends BaseEntity {

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "aadhaar_number", nullable = false, length = 255)
    private String aadhaarNumber;

    @Column(name = "aadhaar_number_hash", nullable = false, length = 64)
    private String aadhaarNumberHash;

    @Column(name = "provider_reference_id", nullable = false, length = 255)
    private String providerReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AadhaarVerificationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_by_worker_id")
    private UUID consumedByWorkerId;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    // --- Verified data, populated only on success; raw as returned by the provider. ---

    @Column(name = "verified_full_name", length = 255)
    private String verifiedFullName;

    @Column(name = "verified_gender", length = 20)
    private String verifiedGender;

    @Column(name = "verified_date_of_birth")
    private LocalDate verifiedDateOfBirth;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "verified_address_line1", length = 255)
    private String verifiedAddressLine1;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "verified_address_line2", length = 255)
    private String verifiedAddressLine2;

    @Column(name = "verified_pincode", length = 10)
    private String verifiedPincode;

    @Column(name = "verified_state_name", length = 150)
    private String verifiedStateName;

    @Column(name = "verified_district_name", length = 150)
    private String verifiedDistrictName;

    @Column(name = "verified_photo_document_id")
    private UUID verifiedPhotoDocumentId;

    protected WorkerAadhaarVerification() {
        // required by JPA
    }

    public WorkerAadhaarVerification(String aadhaarNumber, String aadhaarNumberHash, String providerReferenceId,
                                     Instant expiresAt, Instant consentGivenAt, String consentIp) {
        this.aadhaarNumber = aadhaarNumber;
        this.aadhaarNumberHash = aadhaarNumberHash;
        this.providerReferenceId = providerReferenceId;
        this.status = AadhaarVerificationStatus.OTP_SENT;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.consentGivenAt = consentGivenAt;
        this.consentIp = consentIp;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markFailed() {
        this.status = AadhaarVerificationStatus.FAILED;
    }

    public void markVerified(Instant now, String fullName, String gender, LocalDate dateOfBirth,
                             String addressLine1, String addressLine2, String pincode,
                             String stateName, String districtName, UUID photoDocumentId) {
        this.status = AadhaarVerificationStatus.VERIFIED;
        this.verifiedAt = now;
        this.verifiedFullName = fullName;
        this.verifiedGender = gender;
        this.verifiedDateOfBirth = dateOfBirth;
        this.verifiedAddressLine1 = addressLine1;
        this.verifiedAddressLine2 = addressLine2;
        this.verifiedPincode = pincode;
        this.verifiedStateName = stateName;
        this.verifiedDistrictName = districtName;
        this.verifiedPhotoDocumentId = photoDocumentId;
    }

    public void markConsumed(Instant now, UUID workerId) {
        this.consumedAt = now;
        this.consumedByWorkerId = workerId;
    }
}
