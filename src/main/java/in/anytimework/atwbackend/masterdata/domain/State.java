package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * An Indian state or union territory - stable, government-defined reference data that statutory
 * rules (minimum wage, PF/ESIC/PT) and every address in the system key off. Seeded once via
 * migration; not created through the API.
 */
@Getter
@Entity
@Table(name = "state")
public class State extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** ISO 3166-2:IN subdivision code, e.g. "RJ", "MH" - stable even if the display name changes. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    protected State() {
        // required by JPA
    }

    public State(String name, String code) {
        this.name = name;
        this.code = code;
    }
}
