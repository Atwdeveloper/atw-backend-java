package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import in.anytimework.atwbackend.shared.security.WorkerEncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Bank/payout details for a worker - basic onboarding data (this is not the {@code wallet} module's
 * balance/transaction ledger, which doesn't exist yet; it's just "where would a future payout go").
 * One account per worker for now (legacy allowed several with an app-only "is primary" flag and no DB
 * guarantee of exactly one) - a deliberate simplification, not a dropped requirement; revisit if
 * multi-account is a real, confirmed need. The account number is encrypted at rest
 * ({@link WorkerEncryptedStringConverter}); holder name/bank name/IFSC/branch aren't secret enough to
 * need it - same split as {@code AdminUserBankDetail}.
 */
@Getter
@Entity
@Table(name = "worker_bank_detail")
public class WorkerBankDetail extends BaseEntity {

    @Column(name = "worker_id", nullable = false, unique = true)
    private UUID workerId;

    @Column(name = "account_holder_name", length = 150)
    private String accountHolderName;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Convert(converter = WorkerEncryptedStringConverter.class)
    @Column(name = "account_number", length = 255)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 15)
    private String ifscCode;

    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected WorkerBankDetail() {
        // required by JPA
    }

    public WorkerBankDetail(UUID workerId) {
        this.workerId = workerId;
        this.verified = false;
    }

    public void update(String accountHolderName, String bankName, String accountNumber, String ifscCode, String branchName) {
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.ifscCode = ifscCode;
        this.branchName = branchName;
        // Any change to the account details invalidates a prior verification - must be re-verified.
        this.verified = false;
        this.verifiedAt = null;
    }

    /**
     * No admin endpoint calls this yet in this slice (manual bank verification isn't built) - the
     * column/mutator exist now so the eventual verify action doesn't need a migration, same as how
     * {@code workers.view_wallet} is a real permission with no wired feature yet.
     */
    public void markVerified(Instant verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }
}
