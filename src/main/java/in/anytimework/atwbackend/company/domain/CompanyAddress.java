package in.anytimework.atwbackend.company.domain;

import in.anytimework.atwbackend.shared.kernel.Address;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * One of a company's addresses (its registered office, or any number of branches - see
 * {@link CompanyAddressType}), with exactly one marked {@link #isPrimary()} per company (DB-enforced
 * via {@code uq_company_address_primary}, the same partial-unique-index mechanism as
 * {@code worker.domain.WorkerDesignation.isPrimary}, not {@code worker.domain.WorkerAddress}'s
 * fixed-2-types shape - a company's addresses are a genuinely variable-length list, not exactly one
 * of each of 2 kinds).
 *
 * <p>Real satellite {@code @Entity} with its own UUID PK, never a generic owner_type/owner_id pair -
 * same reasoning as every other satellite table in this codebase ({@code WorkerKycProfile}'s Javadoc
 * explains why a polymorphic pair can never carry a real foreign key). Embeds
 * {@link Address} as-is, plain text (not encrypted) - the same explicit, confirmed-with-the-developer
 * decision {@link Address}'s own Javadoc documents for every other consumer.</p>
 *
 * <p><strong>Soft delete:</strong> {@link #markDeleted} sets {@code deletedAt} rather than ever
 * issuing a real SQL DELETE - identical mechanism and reasoning to {@link Company}/{@link CompanyUser}.
 * {@code @SQLRestriction} rewrites every SQL statement Hibernate generates to load a
 * {@code CompanyAddress} to add {@code AND deleted_at IS NULL}, uniformly and without exception - see
 * {@code CompanyAddressService.delete} for the "at least one address, exactly one primary" rules
 * enforced alongside it, and {@code V38__company_address_soft_delete.sql} for why
 * {@code uq_company_address_primary} had to be redefined to also exclude deleted rows.</p>
 */
@Getter
@Entity
@Table(name = "company_address")
@SQLRestriction("deleted_at is null")
public class CompanyAddress extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 30)
    private CompanyAddressType addressType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Embedded
    private Address address;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CompanyAddress() {
        // required by JPA
    }

    public CompanyAddress(UUID companyId, CompanyAddressType addressType, boolean primary) {
        this.companyId = companyId;
        this.addressType = addressType;
        this.primary = primary;
        this.address = new Address(null, null, null, null, null);
    }

    public void update(String line1, String line2, UUID stateId, UUID cityId, String pincode) {
        this.address.update(line1, line2, stateId, cityId, pincode);
    }

    /** Flips which address is the company's main one - the caller is responsible for ensuring exactly one is ever primary per company (DB partial unique index is the final backstop). */
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    /** Soft-deletes this address - see the class-level Javadoc for what this makes true from here on, and {@code CompanyAddressService.delete} for the rules enforced around it. */
    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }
}
