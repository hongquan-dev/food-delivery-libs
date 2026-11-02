package com.fooddelivery.web.exception;

import com.fooddelivery.web.model.FieldErrorDetail;
import com.fooddelivery.web.status.ApiStatus;

import java.util.List;

/**
 * Business error thrown on purpose by a service, validator or controller when the request breaks a business rule
 * (e.g. product not found, name already taken). {@code GlobalExceptionHandler} turns it into an error
 * {@code ApiResponse} with this status, code, message and field errors, so callers need no try/catch.
 *
 * <pre>
 * throw new BusinessException(ApiStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product 7 not found");
 * throw BusinessException.ofField(ApiStatus.BAD_REQUEST, "INVALID_PAGE", "page", "page must not be 1");
 * </pre>
 *
 * <p>Unchecked, so it needs no {@code throws} clause and rolls back a surrounding {@code @Transactional}.
 * A {@code null} or blank code/message falls back to the status' default, as in {@code ResponseHandler}.
 */
public class BusinessException extends RuntimeException {

    private final ApiStatus status;

    // Business error code, e.g. "PRODUCT_NOT_FOUND"; null means the status' default code
    private final String code;

    // Per-field errors, empty list if none
    private final List<FieldErrorDetail> errors;

    // Error with the status' default code
    public BusinessException(ApiStatus status, String message) {
        this(status, null, message, null);
    }

    // Error with a custom code, without field errors
    public BusinessException(ApiStatus status, String code, String message) {
        this(status, code, message, null);
    }

    /**
     * @param status  a 4xx or 5xx status
     * @param code    business error code, e.g. {@code PRODUCT_NOT_FOUND}; the status' default if {@code null}
     * @param message message for the client; the status' default if {@code null}
     * @param errors  per-field errors, {@code null} or empty if none
     * @throws IllegalArgumentException if {@code status} is {@code null} or 2xx
     */
    public BusinessException(ApiStatus status, String code, String message, List<FieldErrorDetail> errors) {
        super(message);
        if (status == null) {
            throw new IllegalArgumentException("BusinessException status must not be null");
        }
        if (status.isSuccess()) {
            throw new IllegalArgumentException("BusinessException needs an error status (4xx / 5xx), got "
                    + status.value());
        }
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    // Error on a single field, e.g. ofField(BAD_REQUEST, "INVALID_PAGE", "page", "page must not be 1").
    // The message is used both as the top-level message and as the field's message
    public static BusinessException ofField(ApiStatus status, String code, String field, String message) {
        return new BusinessException(status, code, message, List.of(new FieldErrorDetail(field, message)));
    }

    public ApiStatus getStatus() { return status; }

    public String getCode() { return code; }

    public List<FieldErrorDetail> getErrors() { return errors; }
}
