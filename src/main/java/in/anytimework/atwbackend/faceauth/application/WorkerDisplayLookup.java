package in.anytimework.atwbackend.faceauth.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves worker ids to a human-readable name/code, purely so {@code faceauth}'s own error
 * messages and identification results never show a raw worker id to an end user.
 *
 * <p>Deliberately the opposite direction from {@code worker.application}'s own package-info
 * guidance ("a module needing worker data should go through a type in {@code worker.application}"):
 * that advice assumes the caller isn't already a dependency of {@code worker}. Here it's reversed -
 * {@code worker.application.WorkerService} already depends on {@code FaceAuthService}, so a direct
 * {@code faceauth -> worker} dependency the other way would be a cyclic module dependency (one
 * {@code ModularityTests} fails the build on). Defining this interface here, in {@code faceauth},
 * and letting {@code worker} supply the implementation keeps the compile-time dependency one-way
 * (worker -> faceauth, already true today) while still letting the data flow the other way at
 * runtime through Spring's own dependency injection - the same "separated interface" shape this
 * module already uses for {@link FaceAuthPort}, just stretched across a module boundary instead of
 * an external vendor boundary.</p>
 */
public interface WorkerDisplayLookup {

    /** Unknown/deleted ids are simply absent from the result - never an exception. */
    Map<UUID, WorkerDisplayInfo> findByIds(Set<UUID> workerIds);
}
