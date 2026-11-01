package in.anytimework.atwbackend.shared.kernel;

import in.anytimework.atwbackend.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.UUID;

/**
 * Reusable address shape, embedded into each owning module's own address table (e.g.
 * {@code admin_user_address}, later {@code company_address}/{@code worker_address}) rather than
 * living in a shared table of its own - each module stays the sole owner of its rows and keeps its
 * own foreign key, per the one-owner-per-table rule.
 *
 * <p>{@code stateId}/{@code cityId} are plain UUIDs into the masterdata module, never a JPA
 * relationship. Free-text lines are PII (they reveal where someone lives) and are encrypted at
 * rest the same way KYC numbers are; state/city/pincode are not identifying on their own and stay
 * plain so they remain usable in lookups and indexes. {@code latitude}/{@code longitude}/
 * {@code formattedAddress} are schema groundwork for a future geocoding adapter - unpopulated
 * until that exists.</p>
 */
@Getter
@Embeddable
public class Address {

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "line1", length = 255)
    private String line1;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "line2", length = 255)
    private String line2;

    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    protected Address() {
        // required by JPA
    }

    public Address(String line1, String line2, UUID stateId, UUID cityId, String pincode) {
        update(line1, line2, stateId, cityId, pincode);
    }

    public void update(String line1, String line2, UUID stateId, UUID cityId, String pincode) {
        this.line1 = line1;
        this.line2 = line2;
        this.stateId = stateId;
        this.cityId = cityId;
        this.pincode = pincode;
    }
}
