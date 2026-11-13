package com.fooddelivery.web.handler;

import com.fooddelivery.dto.response.ApiResponse;
import com.fooddelivery.dto.response.FieldErrorDetail;
import com.fooddelivery.dto.response.ApiStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * The only way controllers should build their {@code ResponseEntity<ApiResponse<T>>} (never
 * {@code ResponseEntity.ok(...)} directly), so every API answers in the same format.
 *
 * <pre>
 * return ResponseHandler.generateResponse(ApiStatus.OK, ApiResponse.success(order));             // 2xx
 * return ResponseHandler.generateErrorResponse(ApiStatus.NOT_FOUND, "Order 99 not found");       // 4xx / 5xx
 * </pre>
 *
 * <p>Each method only accepts its own kind of status and throws {@link IllegalArgumentException} otherwise. The
 * {@code status} of the body is always set to the HTTP status of the response.
 */
public final class ResponseHandler {

    private ResponseHandler() {}

    // Success response without extra headers
    public static <T> ResponseEntity<ApiResponse<T>> generateResponse(ApiStatus status, ApiResponse<T> body) {
        return generateResponse(status, null, body);
    }

    /**
     * Builds a success response.
     *
     * @param status  a 2xx status
     * @param headers extra response headers, {@code null} if none
     * @param body    a success body, from {@link ApiResponse#success}, {@link ApiResponse#ok} or
     *                {@link ApiResponse#page}
     * @return the response, with the body's {@code status} set to {@code status}
     * @throws IllegalArgumentException if {@code status} is {@code null} or not 2xx, or {@code body} is {@code null}
     *                                  or an error body
     */
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

    // Error with the status' default code and message
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status) {
        return generateErrorResponse(status, null, null, null, null);
    }

    // Error with the status' default code and a custom message
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String message) {
        return generateErrorResponse(status, null, null, message, null);
    }

    // Field errors with the status' default message
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, null, null, errors);
    }

    // Field errors with a custom message
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String message,
                                                                          List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, null, message, errors);
    }

    // Custom business code instead of the status' default one (e.g. ORDER_NOT_FOUND instead of NOT_FOUND)
    public static <T> ResponseEntity<ApiResponse<T>> generateErrorResponse(ApiStatus status, String code, String message,
                                                                          List<FieldErrorDetail> errors) {
        return generateErrorResponse(status, null, code, message, errors);
    }

    /**
     * Builds an error response. The error body is built here, with {@code path} taken from the current request
     * ({@code null} when called outside an HTTP request).
     *
     * @param status  a 4xx or 5xx status
     * @param headers extra response headers, {@code null} if none
     * @param code    business error code, e.g. {@code ORDER_NOT_FOUND}; {@link ApiStatus#code()} if {@code null}
     *                or blank
     * @param message message for the client; {@link ApiStatus#reasonPhrase()} if {@code null} or blank
     * @param errors  per-field errors, {@code null} or empty if none (then omitted from the JSON)
     * @return the response, with the body's {@code status} set to {@code status}
     * @throws IllegalArgumentException if {@code status} is {@code null} or 2xx
     */
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

    // The body carries the same status as the HTTP response
    private static <T> ResponseEntity<ApiResponse<T>> build(ApiStatus status, HttpHeaders headers, ApiResponse<T> body) {
        return new ResponseEntity<>(body.withStatus(status), headers, HttpStatusCode.valueOf(status.value()));
    }

    // Path of the request being handled, e.g. "/orders/7", or null when called outside an HTTP request
    private static String currentRequestPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }
}
