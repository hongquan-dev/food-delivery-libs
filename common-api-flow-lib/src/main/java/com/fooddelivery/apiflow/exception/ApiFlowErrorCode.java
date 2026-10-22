package com.fooddelivery.apiflow.exception;

import com.fooddelivery.web.status.ApiStatus;

// Errors returned by the API flow (body size limit, route lookup, JSON Schema check, validator)
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
