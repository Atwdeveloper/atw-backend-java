package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * A named bundle of {@link AdminPermission}s (the legacy "permission profile"). System roles are
 * protected from edit/delete. Admins can hold multiple roles; effective abilities are the union.
 */
@Getter
@Entity
@Table(name = "admin_role")
public class AdminRole extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean systemRole;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "admin_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AdminPermission> permissions = new HashSet<>();

    protected AdminRole() {
        // required by JPA
    }

    public AdminRole(String name, String description, boolean systemRole) {
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
    }

    public void addPermission(AdminPermission permission) {
        this.permissions.add(permission);
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Full replace: the role grants exactly {@code newPermissions} afterward, nothing more. */
    public void replacePermissions(Set<AdminPermission> newPermissions) {
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
    }
}
