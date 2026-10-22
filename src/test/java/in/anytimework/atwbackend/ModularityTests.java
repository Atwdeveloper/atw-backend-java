package in.anytimework.atwbackend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the modular structure at build time: no cyclic dependencies between modules and
 * no module reaching into another module's internals. Fails the build if a boundary is violated,
 * which is how the approved package-by-domain architecture stays enforced as the app grows.
 */
class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(AtwBackendApplication.class).verify();
    }
}
