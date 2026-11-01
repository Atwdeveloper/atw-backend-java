package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * A city under a {@link State}. Sourced at administrative-district granularity rather than every
 * individual locality or town - deliberately, since a locality-level dataset isn't reliably
 * available and the real locality name still belongs in the free-text line of an address. "City"
 * is the name ATW's forms use for this; the underlying grain is districts.
 */
@Getter
@Entity
@Table(name = "city")
public class City extends BaseEntity {

    @Column(name = "state_id", nullable = false)
    private UUID stateId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    protected City() {
        // required by JPA
    }

    public City(UUID stateId, String name) {
        this.stateId = stateId;
        this.name = name;
    }
}
