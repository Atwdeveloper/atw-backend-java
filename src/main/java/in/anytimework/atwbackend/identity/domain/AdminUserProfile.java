package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ordinary demographic PII for an admin/staff account — lower sensitivity than
 * {@link AdminUserKyc} or {@link AdminUserBankDetail}, kept in its own table so it can carry a
 * looser permission boundary ({@code admin_staff.view}) than those do. Optional: a row exists only once
 * this data has actually been captured.
 */
@Getter
@Entity
@Table(name = "admin_user_profile")
public class AdminUserProfile extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 10)
    private String pincode;

    protected AdminUserProfile() {
        // required by JPA
    }

    public AdminUserProfile(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public void update(LocalDate dateOfBirth, Gender gender, String addressLine, String pincode) {
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.addressLine = addressLine;
        this.pincode = pincode;
    }
}
