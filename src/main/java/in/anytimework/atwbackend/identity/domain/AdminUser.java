package in.anytimework.atwbackend.identity.domain;

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

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A platform-staff admin account. {@code superAdmin} grants unrestricted access (bypasses permission
 * checks); everyone else draws their abilities from assigned {@link Role}s. Login security state
 * (failed attempts, lockout) lives here too.
 */
@Getter
@Entity
@Table(name = "admin_user")
public class AdminUser extends BaseEntity {

    @Column(length = 100, unique = true)
    private String username;

    @Column(nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "is_super_admin", nullable = false)
    private boolean superAdmin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "admin_user_role",
            joinColumns = @JoinColumn(name = "admin_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AdminUser() {
        // required by JPA
    }

    public AdminUser(String username, String email, String passwordHash, String fullName, String phone,
                     boolean superAdmin, boolean mustChangePassword) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.superAdmin = superAdmin;
        this.mustChangePassword = mustChangePassword;
        this.status = AdminStatus.ACTIVE;
        this.failedLoginAttempts = 0;
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(int maxAttempts, Duration lockout, Instant now) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockout);
        }
    }

    public void clearLoginFailures() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void registerSuccessfulLogin(Instant now) {
        clearLoginFailures();
        this.lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    public void assignRole(Role role) {
        this.roles.add(role);
    }

    public void setStatus(AdminStatus status) {
        this.status = status;
    }
}
