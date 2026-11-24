package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * One industry a designation belongs to, plus whether it is that designation's PRIMARY industry
 * (exactly one per designation - see {@code uq_designation_industry_primary}).
 *
 * <p>{@code designationId}/{@code industryId} are plain UUID columns, not JPA relationships - same
 * convention {@link Designation#getSkillCategoryId()} already uses. A real {@code @Entity} with its
 * own synthetic id (rather than a plain {@code @ManyToMany}/{@code @JoinTable} pivot, which is what
 * this relationship used to be) because it needs a repository of its own and a non-key business
 * column ({@code isPrimary}) - the identical reasoning {@code worker.domain.WorkerDesignation}
 * already follows for the same shape of problem (a worker-to-designation join that also needs to
 * track which one is primary).</p>
 */
@Getter
@Entity
@Table(name = "designation_industry")
public class DesignationIndustry extends BaseEntity {

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "industry_id", nullable = false)
    private UUID industryId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected DesignationIndustry() {
        // required by JPA
    }

    public DesignationIndustry(UUID designationId, UUID industryId, boolean primary) {
        this.designationId = designationId;
        this.industryId = industryId;
        this.primary = primary;
    }
}
