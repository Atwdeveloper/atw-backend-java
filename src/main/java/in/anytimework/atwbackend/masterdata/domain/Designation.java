package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * A job title/role (e.g. Helper, Supervisor) that a vacancy is posted under and a worker is hired
 * for. Its industry assignment (including which one is PRIMARY) lives on the separate
 * {@link DesignationIndustry} entity/repository, not a field here - converted from a plain
 * {@code @ManyToMany}/{@code @JoinTable} once the relationship needed to carry that extra
 * {@code isPrimary} business column (see {@code DesignationIndustry}'s own Javadoc).
 *
 * <p>{@code skillCategoryId} is optional and exists purely to resolve the applicable
 * {@link MinimumWageRate} (state x skill category) for this designation - plain UUID, not a JPA
 * relationship, same reasoning as {@link Skill#getDesignationId()}.</p>
 */
@Getter
@Entity
@Table(name = "designation")
public class Designation extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    /** Optional icon, stored by the document module - see MasterDataImageSupport. */
    @Column(name = "image_document_id")
    private UUID imageDocumentId;

    @Column(name = "skill_category_id")
    private UUID skillCategoryId;

    protected Designation() {
        // required by JPA
    }

    public Designation(String name) {
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

    public void setImageDocumentId(UUID imageDocumentId) {
        this.imageDocumentId = imageDocumentId;
    }

    /** Nullable: a designation isn't required to carry a minimum-wage classification. */
    public void assignSkillCategory(UUID skillCategoryId) {
        this.skillCategoryId = skillCategoryId;
    }
}
