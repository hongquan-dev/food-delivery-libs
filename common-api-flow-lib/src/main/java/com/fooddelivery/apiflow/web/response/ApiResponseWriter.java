package com.fooddelivery.apiflow.web.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.web.model.ApiResponse;
import com.fooddelivery.web.status.ApiStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Writes an ApiResponse straight to the servlet response, for code that answers a request itself
// instead of returning from a controller (filters, interceptors)
public class ApiResponseWriter {
    private final ObjectMapper objectMapper;

    public ApiResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(HttpServletRequest request, HttpServletResponse response, ApiStatus status,
                           String code, String message) throws IOException {
        write(response, status, ApiResponse.error(code, message, request.getRequestURI()));
    }

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
