package in.anytimework.atwbackend.shared.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Transport-friendly view of a Spring Data {@link Page}, so controllers never leak
 * the {@code Page}/{@code Pageable} types onto the public API contract.
 *
 * @param <T> the element type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
