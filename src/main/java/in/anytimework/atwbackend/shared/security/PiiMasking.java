package in.anytimework.atwbackend.shared.security;

/** Masks a sensitive identifier for display to a caller without the permission to see it unmasked. */
public final class PiiMasking {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private PiiMasking() {
    }

    /** Replaces every character but the last 4 with '*' (fewer than 4 total: masks all of it). Null/blank passes through. */
    public static String maskTail(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= VISIBLE_SUFFIX_LENGTH) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - VISIBLE_SUFFIX_LENGTH) + value.substring(value.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
