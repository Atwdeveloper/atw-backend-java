package in.anytimework.atwbackend.worker.domain;

/**
 * Self-reported gender on a worker's profile. A worker-owned duplicate of
 * {@code identity.domain.Gender} rather than a shared type: identity has no published cross-module
 * interface (no {@code package-info.java}/{@code @NamedInterface}) for {@code worker} to depend on,
 * and a 3-constant enum is cheap enough to duplicate rather than restructure an already-shipped
 * module for.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
