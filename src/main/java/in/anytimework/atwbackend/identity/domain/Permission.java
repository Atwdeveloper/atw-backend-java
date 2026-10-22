package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A single capability in the catalog, identified by {@code code} ("module.action", e.g.
 * {@code companies.edit}). The code is the authority string enforced by {@code @authz.has(...)}.
 */
@Getter
@Entity
@Table(name = "permission")
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String code;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 150)
    private String label;

    protected Permission() {
        // required by JPA
    }

    public Permission(String code, String module, String action, String label) {
        this.code = code;
        this.module = module;
        this.action = action;
        this.label = label;
    }
}
