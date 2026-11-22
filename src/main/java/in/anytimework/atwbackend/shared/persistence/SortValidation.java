package in.anytimework.atwbackend.shared.persistence;

import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates an incoming {@link Pageable}'s sort properties against a per-entity allowlist of real,
 * directly sortable JPA entity properties, before the {@code Pageable} ever reaches a repository
 * query. Every listing service in this codebase should call {@link #assertSortable} as the first line
 * of its {@code list(...)} method, passing its own small {@code Set<String>} of allowed property names.
 *
 * <p>Without this, an unknown property name surfaces as Spring Data's own
 * {@code PropertyReferenceException} (an unhelpful 500), and there would be nothing stopping a sort
 * request against a DTO-only or cross-module field that isn't a real column at all (e.g. a
 * batch-resolved display name, or - for {@code Worker} - a KYC status that lives in a different
 * module's table) from either failing unpredictably or silently forcing an expensive, non-obvious
 * query plan. Each entity's own allowlist should stick to real, directly queryable scalar columns -
 * see the {@code ALLOWED_SORT} constant in each service for which ones and why.</p>
 */
public final class SortValidation {

    private SortValidation() {
    }

    public static void assertSortable(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new ValidationException("Cannot sort by '" + order.getProperty() + "'. Allowed fields: "
                        + allowedProperties.stream().sorted().collect(Collectors.joining(", ")));
            }
        }
    }
}
