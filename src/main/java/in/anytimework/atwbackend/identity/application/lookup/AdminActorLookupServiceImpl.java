package in.anytimework.atwbackend.identity.application.lookup;

import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class AdminActorLookupServiceImpl implements AdminActorLookupService {

    private final AdminUserRepository adminUsers;

    AdminActorLookupServiceImpl(AdminUserRepository adminUsers) {
        this.adminUsers = adminUsers;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findNames(Set<UUID> adminIds) {
        if (adminIds.isEmpty()) {
            return Map.of();
        }
        return adminUsers.findAllById(adminIds).stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getFullName));
    }
}
