package com.fooddelivery.web.exception;

import com.fooddelivery.web.handler.ResponseHandler;
import com.fooddelivery.dto.response.ApiResponse;
import com.fooddelivery.dto.response.ApiStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every exception that escapes a controller, a service or an api-flow validator into an error
 * {@code ApiResponse}, so controllers only write the success path and need no try/catch.
 *
 * <ul>
 *   <li>{@link BusinessException}: thrown on purpose; answered with its own status, code, message and field errors.</li>
 *   <li>Spring MVC errors that carry a status (e.g. 405 method not allowed, 415 media type, 400 unreadable body or
 *       wrong parameter type): answered with that status and its default code/message.</li>
 *   <li>Anything else: unexpected; answered with 500 {@code INTERNAL_ERROR}, details only in the log.</li>
 * </ul>
 *
 * <p>Registered by {@code CommonWebAutoConfiguration} with the lowest precedence, so a service's own
 * {@code @RestControllerAdvice} wins for the exceptions it handles. Errors returned by a controller through
 * {@code ResponseHandler.generateErrorResponse} do not go through here and are unaffected.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    // 4xx are the client's fault and only logged as a warning; a 5xx business error keeps its stack trace
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        if (e.getStatus().value() >= 500) {
            log.error("Business error {} {}: {}", e.getStatus().value(), e.getCode(), e.getMessage(), e);
        } else {
            log.warn("Business error {} {}: {}", e.getStatus().value(), e.getCode(), e.getMessage());
        }
        return ResponseHandler.generateErrorResponse(e.getStatus(), e.getCode(), e.getMessage(), e.getErrors());
    }

    // Every other exception. Spring's own exceptions that carry a status (ErrorResponse, e.g. 405, 415) and the two
    // common client errors without one (unreadable body, wrong parameter type -> 400) keep their status; anything
    // else is a bug or an infrastructure failure -> 500. Only the status' default code/message is returned, so
    // exception details never reach the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOtherException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            // Headers such as "Allow" of a 405
            return clientError(toApiStatus(errorResponse.getStatusCode().value()), errorResponse.getHeaders(), e);
        }
        if (e instanceof HttpMessageNotReadableException || e instanceof TypeMismatchException) {
            return clientError(ApiStatus.BAD_REQUEST, null, e);
        }
        log.error("Unexpected error", e);
        return ResponseHandler.generateErrorResponse(ApiStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> clientError(ApiStatus status, HttpHeaders headers, Exception e) {
        if (status.value() >= 500) {
            log.error("Request failed with {}", status.value(), e);
        } else {
            log.warn("Request rejected with {}: {}", status.value(), e.getMessage());
        }
        return ResponseHandler.generateErrorResponse(status, headers, null, null, null);
    }

    // Same status if ApiStatus has it, otherwise the closest generic one (400 for 4xx, 500 for anything else)
    private static ApiStatus toApiStatus(int statusCode) {
        for (ApiStatus status : ApiStatus.values()) {
            if (status.value() == statusCode) {
                return status;
            }
        }
        return statusCode >= 400 && statusCode < 500 ? ApiStatus.BAD_REQUEST : ApiStatus.INTERNAL_SERVER_ERROR;
    }
}
