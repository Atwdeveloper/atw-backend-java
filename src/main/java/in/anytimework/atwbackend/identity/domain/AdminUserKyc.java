package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import in.anytimework.atwbackend.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Government-ID-grade KYC data for an admin/staff account (Aadhaar, PAN, UAN, PF, ESIC numbers).
 * Deliberately a separate table from {@link AdminUserProfile}: this data needs a stricter
 * permission boundary ({@code admin_staff.view_kyc}, distinct from {@code admin_staff.view} — see
 * AdminStaffKycService, which also masks these values for callers without that permission) and is
 * encrypted at rest (AES-256-GCM, transparent via {@link EncryptedStringConverter} — see
 * PiiEncryptor and the V13 migration comment). Optional: a row exists only once this data has
 * actually been captured.
 */
@Getter
@Entity
@Table(name = "admin_user_kyc")
public class AdminUserKyc extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "aadhaar_number", length = 255)
    private String aadhaarNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pan_number", length = 255)
    private String panNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "uan_number", length = 255)
    private String uanNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pf_number", length = 255)
    private String pfNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "esic_number", length = 255)
    private String esicNumber;

    protected AdminUserKyc() {
        // required by JPA
    }

    public AdminUserKyc(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public void update(String aadhaarNumber, String panNumber, String uanNumber, String pfNumber, String esicNumber) {
        this.aadhaarNumber = aadhaarNumber;
        this.panNumber = panNumber;
        this.uanNumber = uanNumber;
        this.pfNumber = pfNumber;
        this.esicNumber = esicNumber;
    }
}
