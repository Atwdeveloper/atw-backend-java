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
 * A business industry a company/vacancy/designation can be classified under (e.g. Construction,
 * Manufacturing) - admin-managed reference data, never created through company/worker/vacancy APIs.
 */
@Getter
@Entity
@Table(name = "industry")
public class Industry extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    /** Optional icon, stored by the document module - see MasterDataImageSupport. */
    @Column(name = "image_document_id")
    private UUID imageDocumentId;

    protected Industry() {
        // required by JPA
    }

    public Industry(String name, String code) {
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

    public void setImageDocumentId(UUID imageDocumentId) {
        this.imageDocumentId = imageDocumentId;
    }
}
