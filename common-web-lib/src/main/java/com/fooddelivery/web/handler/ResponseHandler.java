package com.fooddelivery.web.handler;

import com.fooddelivery.web.model.ApiResponse;
import com.fooddelivery.web.model.FieldErrorDetail;
import com.fooddelivery.web.status.ApiStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Returns a response from a controller in one call.
 *
 * <pre>
 * // Success (2xx): body built with ApiResponse.success / ok / page
 * return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order));
 *
 * // Error (4xx / 5xx): the error body is built here, path taken from the current request
 * return ResponseHandler.generateErrorResponse(ApiStatus.NOT_FOUND, "Order 99 not found");           // code NOT_FOUND
 * return ResponseHandler.generateErrorResponse(ApiStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order 99 not found", null);
 * </pre>
 *
 * Each method only accepts its own kind of status, so a success body can never go out with an error status
 * and the other way round.
 */
public final class ResponseHandler {

    private ResponseHandler() {}

    // ===== Success (2xx) =====

    public static <T> ResponseEntity<ApiResponse<T>> generateResponse(ApiStatus status, ApiResponse<T> body) {
        return generateResponse(status, null, body);
    }

    public static <T> ResponseEntity<ApiResponse<T>> generateResponse(ApiStatus status, HttpHeaders headers,
                                                                     ApiResponse<T> body) {
        if (status == null) {
            throw new IllegalArgumentException("Response status must not be null");
        }
        if (!status.isSuccess()) {
            throw new IllegalArgumentException("generateResponse only accepts success statuses (2xx), got "
                    + status.value() + ". Use generateErrorResponse to return an error.");
        }
        if (body == null || !body.success()) {
            throw new IllegalArgumentException("generateResponse needs a success body (ApiResponse.success / ok / page)."
                    + " Use generateErrorResponse to return an error.");
        }
        return build(status, headers, body);
    }

    // ===== Error (4xx / 5xx) =====
    // code defaults to status.code() and message to status.reasonPhrase() when not given

    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status) {
        return generateErrorResponse(status, null, null, null, null);
    }

    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String message) {
        return generateErrorResponse(status, null, null, message, null);
    }

    // Field errors with the status' default message
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, null, null, errors);
    }

    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String message,
                                                                          List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, null, message, errors);
    }

    // Custom business code instead of the status' default one (e.g. ORDER_NOT_FOUND instead of NOT_FOUND)
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String code, String message,
                                                                          List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, code, message, errors);
    }

    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, HttpHeaders headers,
                                                                          String code, String message,
                                                                          List<FieldErrorDetail> errors) {
        if (status == null) {
            throw new IllegalArgumentException("Response status must not be null");
        }
        if (status.isSuccess()) {
            throw new IllegalArgumentException("generateErrorResponse only accepts error statuses (4xx / 5xx), got "
                    + status.value() + ". Use generateResponse to return a success.");
        }
        String finalCode = code == null || code.isBlank() ? status.code() : code;
        String finalMessage = message == null || message.isBlank() ? status.reasonPhrase() : message;
        return build(status, headers, ApiResponse.error(finalCode, finalMessage, errors, currentRequestPath()));
    }

    // ===== Internal =====

    // The body carries the same status as the HTTP response
    private static <T> ResponseEntity<ApiResponse<T>> build(ApiStatus status, HttpHeaders headers, ApiResponse<T> body) {
        return new ResponseEntity<>(body.withStatus(status), headers, HttpStatusCode.valueOf(status.value()));
    }

    // Path of the request being handled, or null when called outside an HTTP request
    private static String currentRequestPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }
}
