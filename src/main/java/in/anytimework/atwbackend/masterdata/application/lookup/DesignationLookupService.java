package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only API of designation reference data. Other modules (vacancy, worker, agent, ...)
 * depend on this narrow type, never on {@code DesignationService} directly.
 */
public interface DesignationLookupService {

    List<DesignationSummary> list();

    DesignationSummary get(UUID id);

    boolean exists(UUID id);
}
