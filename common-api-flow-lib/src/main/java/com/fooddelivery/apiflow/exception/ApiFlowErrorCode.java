package com.fooddelivery.apiflow.exception;

import com.fooddelivery.dto.response.ApiStatus;

/**
 * Errors the API flow returns to the client at request time, as an {@code ApiResponse} (see common-dto-lib).
 * The enum name is sent as the {@code code} field of the response.
 *
 * <ul>
 *   <li>{@link #PAYLOAD_TOO_LARGE} (413): JSON body larger than {@code api-flow.max-body-size}; RequestCachingFilter.</li>
 *   <li>{@link #API_NOT_FOUND} (404): method + path not declared in api-routes.xml, or no controller handles it.</li>
 *   <li>{@link #INVALID_REQUEST_BODY} (400): body missing, not JSON, or not matching the route's JSON Schema.</li>
 *   <li>{@link #REQUEST_VALIDATION_FAILED} (400): a {@code boolean} validator returned {@code false}. A validator that
 *       throws {@code BusinessException} instead answers with its own code and field errors.</li>
 * </ul>
 */
public enum ApiFlowErrorCode {
    PAYLOAD_TOO_LARGE(ApiStatus.PAYLOAD_TOO_LARGE, "Request body too large"),
    API_NOT_FOUND(ApiStatus.NOT_FOUND, "API not found"),
    INVALID_REQUEST_BODY(ApiStatus.BAD_REQUEST, "Request body does not match the expected structure"),
    REQUEST_VALIDATION_FAILED(ApiStatus.BAD_REQUEST, "Request data failed validation");

    private final ApiStatus status;
    private final String message;

    ApiFlowErrorCode(ApiStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getCode() { return name(); }

    public ApiStatus getStatus() { return status; }

    public String getMessage() { return message; }
}
