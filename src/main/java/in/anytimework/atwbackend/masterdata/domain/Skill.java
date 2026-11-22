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
 * A specific skill offered under exactly one {@link Designation} (e.g. "Forklift Operation" under
 * "Warehouse Helper") - plain {@code designationId}, not a JPA relationship, matching how
 * {@code AdminUserProfile}/{@code AdminUserAddress} hold {@code adminUserId}: a simple parent
 * pointer doesn't need to be a navigable association. Name is unique per designation, not globally -
 * the same skill name can legitimately exist under two different designations.
 */
@Getter
@Entity
@Table(name = "skill")
public class Skill extends BaseEntity {

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    /** Optional icon, stored by the document module - see MasterDataImageSupport. */
    @Column(name = "image_document_id")
    private UUID imageDocumentId;

    protected Skill() {
        // required by JPA
    }

    public Skill(UUID designationId, String name) {
        this.designationId = designationId;
        this.name = name;
        this.status = ActiveStatus.ACTIVE;
    }

    public void update(UUID designationId, String name) {
        this.designationId = designationId;
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
}
