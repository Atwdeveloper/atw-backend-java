package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminPermission;
import in.anytimework.atwbackend.identity.domain.AdminPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serves the permission catalog (used by the role editor to build the permission matrix). */
@Service
public class PermissionCatalogService {

    private final AdminPermissionRepository permissions;

    public PermissionCatalogService(AdminPermissionRepository permissions) {
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<AdminPermission> catalog() {
        return permissions.findAllByOrderByModuleAscActionAsc();
    }
}
