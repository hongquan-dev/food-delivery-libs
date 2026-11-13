package com.fooddelivery.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.dto.response.ApiResponse;
import com.fooddelivery.dto.response.ApiStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an {@link ApiResponse} directly to the servlet response, for code that answers outside a controller: servlet
 * filters and interceptors, e.g. {@code RequestCachingFilter} and {@code ApiFlowInterceptor} of common-api-flow-lib or a
 * service's own filter. Uses the application's ObjectMapper, so the JSON looks the same as a controller response.
 *
 * <p>Registered as a bean by {@code CommonWebAutoConfiguration}; inject it instead of creating one.
 */
public class ApiResponseWriter {
    private final ObjectMapper objectMapper;

    public ApiResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Error response with the given code and message; "path" is set to the request URI
    public void writeError(HttpServletRequest request, HttpServletResponse response, ApiStatus status,
                           String code, String message) throws IOException {
        write(response, status, ApiResponse.error(code, message, request.getRequestURI()));
    }

    // Sets the HTTP status and writes the body as UTF-8 JSON, with "status" in the body matching the HTTP status.
    // Does nothing if the response is already committed (headers already sent)
    public void write(HttpServletResponse response, ApiStatus status, ApiResponse<?> body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body.withStatus(status));
    }
}
