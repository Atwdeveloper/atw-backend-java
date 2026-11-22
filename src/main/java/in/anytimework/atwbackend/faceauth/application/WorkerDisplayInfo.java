package in.anytimework.atwbackend.faceauth.application;

/**
 * A worker's display identity, exactly as {@code faceauth} needs it to show a human-readable
 * reference instead of a raw id - see {@link WorkerDisplayLookup}'s Javadoc for why this module
 * resolves one at all.
 */
public record WorkerDisplayInfo(String fullName, String workerCode) {
}
