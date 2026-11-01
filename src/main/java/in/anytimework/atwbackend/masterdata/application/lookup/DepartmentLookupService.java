package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only API of department reference data. {@code identity} (admin staff org
 * assignment) and later {@code agent} depend on this narrow type, never on
 * {@code DepartmentService} directly.
 */
public interface DepartmentLookupService {

    List<DepartmentSummary> list();

    DepartmentSummary get(UUID id);

    boolean exists(UUID id);
}
