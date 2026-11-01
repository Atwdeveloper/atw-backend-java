package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only API of industry reference data. Other modules (company, vacancy, agent, ...)
 * depend on this narrow type, never on {@code IndustryService} directly - structurally, a consumer
 * typed against this interface cannot create/edit an industry, only read it.
 */
public interface IndustryLookupService {

    List<IndustrySummary> list();

    IndustrySummary get(UUID id);

    boolean exists(UUID id);
}
