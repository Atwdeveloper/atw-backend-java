package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A job title/role (e.g. Helper, Supervisor) that a vacancy is posted under and a worker is hired
 * for. A designation can apply to multiple industries - same many-to-many shape as
 * {@code AdminUser.roles}, including the batch-fetch to avoid N+1 when a page of designations is listed.
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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "designation_industry",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "industry_id"))
    @BatchSize(size = 50)
    private Set<Industry> industries = new HashSet<>();

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

    /** Full replace: the designation applies to exactly {@code newIndustries} afterward, nothing more. */
    public void replaceIndustries(Set<Industry> newIndustries) {
        this.industries.clear();
        this.industries.addAll(newIndustries);
    }
}
