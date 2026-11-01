package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only API of skill-category reference data. Other modules (payroll, vacancy, ...)
 * depend on this narrow type, never on {@code SkillCategoryService} directly.
 */
public interface SkillCategoryLookupService {

    List<SkillCategorySummary> list();

    SkillCategorySummary get(UUID id);

    boolean exists(UUID id);
}
