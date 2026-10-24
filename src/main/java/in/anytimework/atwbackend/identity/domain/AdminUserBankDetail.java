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
 * Bank/payout details for an admin/staff account. A separate table from {@link AdminUserProfile}
 * and {@link AdminUserKyc}: financial data needs its own permission boundary
 * ({@code admin_staff.view_bank_details} — see AdminStaffBankDetailService, which also masks the account
 * number for callers without it) and its own audit trail — this table's
 * {@code updated_by}/{@code updated_at}/{@code version} record who last changed where someone's pay
 * goes, independent of admin_user's own audit trail. The account number is encrypted at rest
 * (AES-256-GCM via {@link EncryptedStringConverter}); the other fields (holder name, bank name,
 * IFSC, branch) aren't secret enough to need it. Optional: a row exists only once this data has
 * actually been captured.
 */
@Getter
@Entity
@Table(name = "admin_user_bank_detail")
public class AdminUserBankDetail extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Column(name = "account_holder_name", length = 150)
    private String accountHolderName;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", length = 255)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 15)
    private String ifscCode;

    @Column(name = "branch_name", length = 150)
    private String branchName;

    protected AdminUserBankDetail() {
        // required by JPA
    }

    public AdminUserBankDetail(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public void update(String accountHolderName, String bankName, String accountNumber, String ifscCode, String branchName) {
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.ifscCode = ifscCode;
        this.branchName = branchName;
    }
}
