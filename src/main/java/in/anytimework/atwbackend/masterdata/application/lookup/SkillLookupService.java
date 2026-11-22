package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only API of skill reference data. Other modules (vacancy, worker, ...) depend on
 * this narrow type, never on {@code SkillService} directly.
 */
public interface SkillLookupService {

    List<SkillSummary> list();

    List<SkillSummary> listByDesignation(UUID designationId);

    SkillSummary get(UUID id);

    boolean exists(UUID id);
}
