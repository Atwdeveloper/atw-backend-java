package in.anytimework.atwbackend.company.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A login account for one person acting on behalf of a {@link Company} - its own parallel chain to
 * {@code identity.domain.AdminUser}, deliberately not folded into the identity module (this
 * codebase's locked rule is to group by business domain, never by user type). Only
 * {@link CompanyUserRole#OWNER} is created as of this slice (see {@link CompanyUserRole}'s Javadoc).
 *
 * <p>{@link #status} ({@link CompanyUserStatus}) is this login's own active/disabled state -
 * deliberately a different enum from {@link CompanyStatus} (the owning company's approval
 * lifecycle); see {@link CompanyUserStatus}'s Javadoc for how {@code CompanyService} keeps the two in
 * sync in one direction without conflating them.</p>
 *
 * <p><strong>Soft delete:</strong> {@link #markDeleted} mirrors {@code Worker}/{@code AdminUser}
 * exactly - {@code @SQLRestriction} excludes a deleted row from every query path automatically.</p>
 */
@Getter
@Entity
@Table(name = "company_user")
@SQLRestriction("deleted_at is null")
public class CompanyUser extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private CompanyUserRole role;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Unique only among non-deleted company users - a partial index, not {@code unique = true} here; see V37__company.sql. Lowercased at the source, same V12-admin_user precedent. */
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompanyUserStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "security_version", nullable = false)
    private int securityVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CompanyUser() {
        // required by JPA
    }

    /**
     * Always starts {@link CompanyUserStatus#ACTIVE}, {@code mustChangePassword=true},
     * {@code securityVersion=1} - the caller (see {@code CompanyService.create}) always supplies an
     * encoded random, never-disclosed placeholder password; the account cannot be used to log in
     * until the invite link is used to set a real one.
     */
    public CompanyUser(UUID companyId, CompanyUserRole role, String name, String email, String phone, String passwordHash) {
        this.companyId = companyId;
        this.role = role;
        this.name = name;
        this.email = email.toLowerCase(Locale.ROOT);
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = CompanyUserStatus.ACTIVE;
        this.mustChangePassword = true;
        this.securityVersion = 1;
    }

    public boolean isActive() {
        return status == CompanyUserStatus.ACTIVE;
    }

    public void disable() {
        this.status = CompanyUserStatus.DISABLED;
    }

    public void activate() {
        this.status = CompanyUserStatus.ACTIVE;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    /** Bumps the security version, invalidating any session/token minted under the old value. */
    public void bumpSecurityVersion() {
        this.securityVersion++;
    }

    public void registerSuccessfulLogin(Instant now) {
        this.lastLoginAt = now;
    }

    /** Soft-deletes this company user - see {@code Company}'s class-level Javadoc for the cascade this is always part of, never called on its own. */
    public void markDeleted(Instant at) {
        this.deletedAt = at;
    }
}
