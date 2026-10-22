package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.Permission;
import in.anytimework.atwbackend.identity.domain.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serves the permission catalog (used by the role editor to build the permission matrix). */
@Service
public class PermissionCatalogService {

    private final PermissionRepository permissions;

    public PermissionCatalogService(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<Permission> catalog() {
        return permissions.findAllByOrderByModuleAscActionAsc();
    }
}
