package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminAuditLog;
import in.anytimework.atwbackend.identity.domain.AdminAuditLogRepository;
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

    public AdminAuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorAdminId, String action, String detail, String ipAddress, String userAgent) {
        String actorType = actorAdminId == null ? "SYSTEM" : "ADMIN";
        repository.save(new AdminAuditLog(actorAdminId, actorType, action, null, null, detail,
                ipAddress, userAgent, MDC.get("traceId")));
    }
}
