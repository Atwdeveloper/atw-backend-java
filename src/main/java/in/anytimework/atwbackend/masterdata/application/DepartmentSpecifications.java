package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.domain.Department;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;

final class DepartmentSpecifications {

    private DepartmentSpecifications() {
    }

    static Specification<Department> hasStatuses(Set<ActiveStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Department> nameContains(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }
}
