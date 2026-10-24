package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminAuditLog;
import in.anytimework.atwbackend.identity.domain.AdminAuditLogRepository;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records security-significant admin actions. Writes run in a new transaction so the audit entry
 * persists even when the surrounding operation fails and rolls back (e.g. a failed login).
 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository repository;
    private final AdminUserRepository adminUsers;

    public AdminAuditService(AdminAuditLogRepository repository, AdminUserRepository adminUsers) {
        this.repository = repository;
        this.adminUsers = adminUsers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorAdminId, String action, String detail, String ipAddress, String userAgent) {
        String actorType = actorAdminId == null ? "SYSTEM" : "ADMIN";
        // Snapshot the actor's email now, not a live join at read time - admin_user.id can later be
        // deleted (ON DELETE SET NULL on actor_admin_id), and this is what keeps the entry
        // attributable even then. See the V14 migration comment.
        String actorEmail = actorAdminId == null ? null
                : adminUsers.findById(actorAdminId).map(AdminUser::getEmail).orElse(null);
        repository.save(new AdminAuditLog(actorAdminId, actorEmail, actorType, action, null, null, detail,
                ipAddress, userAgent, MDC.get("traceId")));
    }
}
