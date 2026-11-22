package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * An internal ATW organizational department (e.g. HR, Operations) that an admin staff account or
 * agent belongs to. Unrelated to industry/designation/skill - legacy never links it to companies,
 * vacancies, or workers either. No image: legacy never had one for department.
 */
@Getter
@Entity
@Table(name = "department")
public class Department extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    protected Department() {
        // required by JPA
    }

    public Department(String name) {
        this.name = name;
        this.status = ActiveStatus.ACTIVE;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void activate() {
        this.status = ActiveStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ActiveStatus.INACTIVE;
    }
}
