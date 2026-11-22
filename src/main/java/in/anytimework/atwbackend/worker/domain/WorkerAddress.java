package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.kernel.Address;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * A worker's address - unlike an admin/staff account (exactly one address, see
 * {@code AdminUserAddress}), a worker genuinely has two distinct addresses worth recording: where
 * they permanently live and where they currently are for work. {@code addressType} distinguishes the
 * two; at most one row per type per worker (see {@code uq_worker_address_worker_type}).
 *
 * <p>Stored as plain text, not encrypted - confirmed with the developer 2026-11-13. This is a
 * deliberate difference from a worker's KYC numbers/bank details, which stay encrypted: an address is
 * considered lower-sensitivity than an Aadhaar/PAN number or a bank account number. See
 * {@code Address}'s Javadoc for the embeddable's own note on this.</p>
 */
@Getter
@Entity
@Table(name = "worker_address")
public class WorkerAddress extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    @Embedded
    private Address address;

    protected WorkerAddress() {
        // required by JPA
    }

    public WorkerAddress(UUID workerId, AddressType addressType) {
        this.workerId = workerId;
        this.addressType = addressType;
        this.address = new Address(null, null, null, null, null);
    }

    public void update(String line1, String line2, UUID stateId, UUID cityId, String pincode) {
        this.address.update(line1, line2, stateId, cityId, pincode);
    }
}
