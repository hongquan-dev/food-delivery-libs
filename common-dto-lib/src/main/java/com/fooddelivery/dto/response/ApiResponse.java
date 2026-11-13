package com.fooddelivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;

/**
 * The single JSON response format of every API, for both success and error.
 *
 * <pre>
 * { "success": true, "status": 200, "code": "SUCCESS", "message": "Success", "data": {...}, "timestamp": "..." }
 * { "success": false, "status": 400, "code": "BAD_REQUEST", "message": "...", "data": null,
 *   "errors": [{ "field": "price", "message": "..." }], "timestamp": "...", "path": "/orders/7" }
 * </pre>
 *
 * <p>Factories leave {@code status} {@code null}; {@code ResponseHandler} or {@code ApiResponseWriter} (common-web-lib)
 * fills it via {@link #withStatus} so it always matches the real HTTP status. {@code meta} and {@code path} are omitted
 * from the JSON when {@code null}, {@code errors} also when empty. Immutable.
 *
 * <p>Plain Java with Jackson annotations only, so a service without Spring MVC (e.g. the WebFlux gateway) can answer
 * in the same format.
 *
 * @param success   {@code true} for a 2xx response, {@code false} for an error
 * @param status    HTTP status of the response, {@code null} until {@link #withStatus} is called
 * @param code      {@code SUCCESS} on success, otherwise an error code, e.g. {@code NOT_FOUND}
 * @param message   human-readable message for the client
 * @param data      result of the API, {@code null} for errors and for {@link #ok()}
 * @param errors    per-field errors of an error response, {@code null} if none
 * @param meta      pagination info, set only by {@link #page}
 * @param timestamp when the body was created
 * @param path      request URI of an error response, {@code null} on success or outside an HTTP request
 */
@JsonPropertyOrder({"success", "status", "code", "message", "data", "errors", "meta", "timestamp", "path"})
public record ApiResponse<T>(
        boolean success,
        Integer status,
        String code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDetail> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) PageMeta meta,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path
) {
    public static final String SUCCESS_CODE = "SUCCESS";
    public static final String SUCCESS_MESSAGE = "Success";

    // Success body with the default message "Success"
    public static <T> ApiResponse<T> success(T data) {
        return success(data, SUCCESS_MESSAGE);
    }

    // Success body with a custom message, e.g. "Order created"
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, null, SUCCESS_CODE, message, data, null, null, Instant.now(), null);
    }

    // For actions without a result (e.g. delete): HTTP 200 with "data": null, so every response has a body
    public static ApiResponse<Void> ok() {
        return success(null);
    }

    // Paginated list: items in "data", pagination info in "meta"
    public static <T> ApiResponse<List<T>> page(List<T> items, PageMeta meta) {
        return new ApiResponse<>(true, null, SUCCESS_CODE, SUCCESS_MESSAGE, items, null, meta, Instant.now(), null);
    }

    // Error body; controllers normally go through ResponseHandler.generateErrorResponse instead
    public static <T> ApiResponse<T> error(String code, String message, String path) {
        return error(code, message, null, path);
    }

    // Error body with per-field errors; errors may be null or empty
    public static <T> ApiResponse<T> error(String code, String message, List<FieldErrorDetail> errors, String path) {
        return new ApiResponse<>(false, null, code, message, null, errors, null, Instant.now(), path);
    }

    // Copy carrying the HTTP status of the response; used by ResponseHandler and ApiResponseWriter
    public ApiResponse<T> withStatus(ApiStatus status) {
        return new ApiResponse<>(success, status.value(), code, message, data, errors, meta, timestamp, path);
    }
}
