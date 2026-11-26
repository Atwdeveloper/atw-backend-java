package in.anytimework.atwbackend.shared.kernel;

/**
 * Which kind of principal performed a tracked action (a status change, a note, ...) - the small,
 * fixed set of actor kinds this app currently recognizes, paired with {@link ActorRef} wherever a
 * response needs to show "who did this" as more than a raw id.
 */
public enum ActorType {
    /** An authenticated platform admin (the only kind of actor that exists almost everywhere today). */
    ADMIN,
    /** No authenticated principal was involved - an automatic, background-triggered transition. */
    SYSTEM,
    /** An authenticated company user. No current caller produces this value yet - reserved for when
     *  a company-initiated action needs the same "who did this" treatment. */
    COMPANY
}
