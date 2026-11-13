package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.kernel.Address;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * An admin/staff account's contact address - exactly one per admin, so unlike company (branches)
 * or worker (permanent/current) there is no address-type column here; one would carry no
 * information. A separate table from {@link AdminUserProfile}, same reasoning as
 * {@link AdminUserKyc}: its own optional row, independently evolvable schema.
 *
 * <p>Stored as plain text, not encrypted - confirmed with the developer 2026-11-13. This is a
 * deliberate difference from {@link AdminUserKyc}/{@link AdminUserBankDetail}, which stay encrypted:
 * an address is considered lower-sensitivity than an Aadhaar/PAN/UAN/PF/ESIC number or a bank account
 * number. See {@code Address}'s Javadoc for the embeddable's own note on this.</p>
 */
@Getter
@Entity
@Table(name = "admin_user_address")
public class AdminUserAddress extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Embedded
    private Address address;

    protected AdminUserAddress() {
        // required by JPA
    }

    public AdminUserAddress(UUID adminUserId) {
        this.adminUserId = adminUserId;
        this.address = new Address(null, null, null, null, null);
    }

    public void update(String line1, String line2, UUID stateId, UUID cityId, String pincode) {
        this.address.update(line1, line2, stateId, cityId, pincode);
    }
}
