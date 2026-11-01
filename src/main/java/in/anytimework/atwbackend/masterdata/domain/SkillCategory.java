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
 * The grouping a {@link Designation} can optionally belong to purely for minimum-wage purposes
 * (e.g. "Unskilled", "Skilled") - separate from the industry/designation/skill chain, matching
 * legacy's own separation (its CRUD lived with the minimum-wage screen, not the
 * industry/designation/skill one). {@link in.anytimework.atwbackend.masterdata.domain.MinimumWageRate}
 * is keyed on (state, skill category), never directly on designation.
 */
@Getter
@Entity
@Table(name = "skill_category")
public class SkillCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    protected SkillCategory() {
        // required by JPA
    }

    public SkillCategory(String name, String code) {
        this.name = name;
        this.code = code;
        this.status = ActiveStatus.ACTIVE;
    }

    public void rename(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public void activate() {
        this.status = ActiveStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ActiveStatus.INACTIVE;
    }
}
