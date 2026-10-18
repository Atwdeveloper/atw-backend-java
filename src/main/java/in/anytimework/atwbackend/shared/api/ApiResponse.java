package in.anytimework.atwbackend.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope returned by every API endpoint, so clients always see the same shape.
 *
 * <p>On success: {@code success=true}, {@code data} holds the payload, {@code error} is null.
 * On failure: {@code success=false}, {@code data} is null, {@code error} describes the problem.
 * Null fields are omitted from the JSON.</p>
 *
 * @param <T> the payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /** Success response carrying a payload. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** Success response with no payload (e.g. a delete). */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** Failure response carrying error detail. */
    public static ApiResponse<Void> error(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
