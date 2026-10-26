package com.fooddelivery.apiflow.web.interceptor;

import com.fooddelivery.apiflow.exception.ApiFlowErrorCode;
import com.fooddelivery.apiflow.route.ApiRegistry;
import com.fooddelivery.apiflow.route.RouteDefinition;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import com.fooddelivery.apiflow.validation.JsonSchemaUtil;
import com.fooddelivery.apiflow.validation.ValidationRequest;
import com.fooddelivery.apiflow.web.filter.CachedBodyHttpServletRequest;
import com.fooddelivery.apiflow.web.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Core of the API flow, run before every controller call on non-excluded paths. Checks in order, stopping at the
 * first failure: route declared and handled by a controller (404 {@code API_NOT_FOUND}), body matches the route's
 * {@code <schema>} (400 {@code INVALID_REQUEST_BODY}), route's {@code <validator>} returns {@code true}
 * (400 {@code REQUEST_VALIDATION_FAILED}). Rejected requests never reach the controller.
 */
public class ApiFlowInterceptor implements HandlerInterceptor {
    private static final Logger log = LogManager.getLogger(ApiFlowInterceptor.class);

    private final ApiRegistry apiRegistry;
    private final ApiResponseWriter apiResponseWriter;

    public ApiFlowInterceptor(ApiRegistry apiRegistry, ApiResponseWriter apiResponseWriter) {
        this.apiRegistry = apiRegistry;
        this.apiResponseWriter = apiResponseWriter;
    }

    // Returns true to continue to the controller, false after writing an error response
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        RouteDefinition route = apiRegistry.getRoute(method, path);

        if (route == null) {
            log.warn("API not declared in routes config: [{}] {}", method, path);
            reject(request, response, ApiFlowErrorCode.API_NOT_FOUND);
            return false;
        }

        // Not a controller method (e.g. the static resource handler that Spring falls back to)
        if (!(handler instanceof HandlerMethod)) {
            log.warn("No controller method mapped for declared API: [{}] {}", method, path);
            reject(request, response, ApiFlowErrorCode.API_NOT_FOUND);
            return false;
        }

        ResolvedValidator validator = route.getResolvedValidator();
        boolean hasValidator = validator != null;

        // The body is only needed (and decoded) when a schema or a validator will look at it
        String requestBody = "";
        if (route.getSchema() != null || hasValidator) {
            requestBody = CachedBodyHttpServletRequest.readCachedBody(request);
        }

        if (route.getSchema() != null && !JsonSchemaUtil.validate(route.getSchema(), requestBody)) {
            log.warn("Request body does not match schema {}: [{}] {}", route.getSchemaPath(), method, path);
            reject(request, response, ApiFlowErrorCode.INVALID_REQUEST_BODY);
            return false;
        }

        if (hasValidator) {
            ValidationRequest validationRequest = new ValidationRequest(request, route.extractPathVariables(path), requestBody);
            // Stored so that a controller parameter of type ValidationRequest receives this same instance
            request.setAttribute(ValidationRequest.ATTRIBUTE, validationRequest);
            // Exceptions thrown by the validator propagate to Spring's exception handling
            if (!validator.validate(validationRequest)) {
                log.warn("Validator {} rejected the request: [{}] {}", route.getValidator(), method, path);
                reject(request, response, ApiFlowErrorCode.REQUEST_VALIDATION_FAILED);
                return false;
            }
        }

        return true;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ApiFlowErrorCode error) throws Exception {
        apiResponseWriter.writeError(request, response, error.getStatus(), error.getCode(), error.getMessage());
    }
}
