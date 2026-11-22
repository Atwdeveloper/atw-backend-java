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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A platform-staff admin account. {@code superAdmin} grants unrestricted access (bypasses permission
 * checks); everyone else draws their abilities from assigned {@link AdminRole}s.
 *
 * <p><strong>Soft delete:</strong> {@link #markDeleted} sets {@code deletedAt} rather than the row
 * ever being physically removed - see {@code AdminStaffService.delete()}'s Javadoc for the full
 * business reasoning, and {@code worker.domain.Worker}'s identical, earlier-built precedent for this
 * exact mechanism. {@code @SQLRestriction} is Hibernate's own documented mechanism for exactly this: it
 * rewrites <em>every</em> SQL statement Hibernate generates to load an {@code AdminUser} - {@code
 * findById}, {@code findByUsernameIgnoreCaseOrEmailIgnoreCase}, {@code existsByUsernameIgnoreCase},
 * the pessimistic-lock JPQL in {@code findByIdForUpdate} - to add {@code AND deleted_at IS NULL},
 * uniformly and without exception. A deleted admin therefore simply stops being findable or able to
 * log in anywhere in this codebase once {@link #markDeleted} is saved, with no other method needing to
 * check a "deleted" flag itself.</p>
 */
@Getter
@Entity
@Table(name = "admin_user")
@SQLRestriction("deleted_at is null")
public class AdminUser extends BaseEntity {

    /** Unique only among non-deleted admins - a partial index, not {@code unique = true} here; see V29__admin_user_soft_delete.sql. */
    @Column(length = 100)
    private String username;

    /** Unique only among non-deleted admins - a partial index, not {@code unique = true} here; see V29__admin_user_soft_delete.sql. */
    @Column(nullable = false, length = 320)
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

    // Dormant: account-level lockout was removed (per-IP rate limiting + mandatory OTP are the
    // brute-force defense now - a global per-account lock let anyone who knew an admin's email
    // keep locking the real admin out indefinitely). Left mapped, always 0/null, so a future
    // reintroduction doesn't need a new migration.
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "security_version", nullable = false)
    private int securityVersion;

    // BatchSize: when a page of admins is listed, their role collections load in batches of 50
    // instead of one query per admin (N+1) - plenty for this table's realistic scale (platform
    // staff, not a high-volume consumer-facing list).
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "admin_user_role",
            joinColumns = @JoinColumn(name = "admin_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @BatchSize(size = 50)
    private Set<AdminRole> roles = new HashSet<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AdminUser() {
        // required by JPA
    }

    public AdminUser(String username, String email, String passwordHash, String fullName, String phone,
                     boolean superAdmin, boolean mustChangePassword) {
        // Lowercased at the source: login and every pre-create duplicate check treat these
        // case-insensitively (findByUsernameIgnoreCaseOrEmailIgnoreCase, existsBy...IgnoreCase), so
        // storage must be canonically normalized or two differently-cased values could both pass
        // the plain DB unique constraint - see V12's migration comment for the full rationale.
        this.username = normalizeUsername(username);
        this.email = email.toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.superAdmin = superAdmin;
        this.mustChangePassword = mustChangePassword;
        this.status = AdminStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.securityVersion = 1;
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
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

    /** Bumps the security version, invalidating any session/token minted under the old value. */
    public void bumpSecurityVersion() {
        this.securityVersion++;
    }

    public void assignRole(AdminRole role) {
        this.roles.add(role);
    }

    public void setStatus(AdminStatus status) {
        this.status = status;
    }

    public void updateProfile(String username, String fullName, String phone) {
        this.username = normalizeUsername(username);
        this.fullName = fullName;
        this.phone = phone;
    }

    /**
     * Blank ("" or whitespace-only, not just null) normalizes to null, not a literal stored value -
     * otherwise two different staff created with an unset username (e.g. a frontend sending "" for
     * "not provided") would both store "" and collide on the unique constraint (a confusing, generic
     * 409 for a field the caller never meant to set), because AdminStaffService's pre-create duplicate
     * check (StringUtils.hasText) already treats blank as absent but storage didn't match that.
     */
    private static String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.toLowerCase(Locale.ROOT) : null;
    }

    /** Full replace: the admin holds exactly {@code newRoles} afterward, nothing more. */
    public void replaceRoles(Set<AdminRole> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    /** Soft-deletes this admin - see the class-level Javadoc on {@code @SQLRestriction} for what this makes true from here on. */
    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }
}
