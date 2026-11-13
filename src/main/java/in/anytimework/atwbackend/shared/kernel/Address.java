package in.anytimework.atwbackend.shared.kernel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.UUID;

/**
 * Reusable address shape, embedded into each owning module's own address table (e.g.
 * {@code admin_user_address}, {@code worker_address}, later {@code company_address}) rather than
 * living in a shared table of its own - each module stays the sole owner of its rows and keeps its
 * own foreign key, per the one-owner-per-table rule.
 *
 * <p>{@code stateId}/{@code cityId} are plain UUIDs into the masterdata module, never a JPA
 * relationship. {@code latitude}/{@code longitude}/{@code formattedAddress} are schema groundwork
 * for a future geocoding adapter - unpopulated until that exists.</p>
 *
 * <p><strong>Stored as plain text, not encrypted</strong> - confirmed with the developer 2026-11-13,
 * a deliberate choice treating a free-text address line as lower-sensitivity than an Aadhaar/PAN/bank
 * number (which stay encrypted via {@code EncryptedStringConverter}/{@code WorkerEncryptedStringConverter}
 * on their own entities). Earlier in this project's history {@code Address} briefly carried its own
 * {@code @Convert}, before encryption was removed entirely from this embeddable - no entity embedding
 * {@code Address} should add one back without the same explicit confirmation this change itself got,
 * since the per-consumer-key machinery ({@code PiiEncryptionConfig}) this would need to resurrect had
 * its own prior bug (one consumer silently inheriting another's key) that is simplest to never
 * reintroduce at all.</p>
 */
@Getter
@Embeddable
public class Address {

    @Column(name = "line1", length = 255)
    private String line1;

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
