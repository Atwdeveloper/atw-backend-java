package in.anytimework.atwbackend.shared.persistence;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Combines a list of optional filter predicates into one {@link Specification}, or {@code null} if
 * the list is empty. Deliberately NOT built by starting from {@code Specification.where(null)} and
 * chaining {@code .and(...)}: that pattern throws {@code IllegalArgumentException: Specification must
 * not be null} on this project's Spring Data JPA version (an ambiguous overload with the newer
 * {@code PredicateSpecification} family) - first hit and worked around in {@code WorkerService.list()}.
 * Every listing service in this codebase should build its filters as a
 * {@code List<Specification<T>>}, adding one conditionally per non-empty criterion, and call
 * {@link #allOf} instead of re-deriving this workaround.
 */
public final class Specifications {

    private Specifications() {
    }

    public static <T> Specification<T> allOf(List<Specification<T>> predicates) {
        return predicates.stream().reduce(Specification::and).orElse(null);
    }
}
