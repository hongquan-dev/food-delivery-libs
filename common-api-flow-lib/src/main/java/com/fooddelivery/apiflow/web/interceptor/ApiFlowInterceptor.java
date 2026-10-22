package com.fooddelivery.apiflow.web.interceptor;

import com.fooddelivery.apiflow.exception.ApiFlowErrorCode;
import com.fooddelivery.apiflow.route.ApiRegistry;
import com.fooddelivery.apiflow.route.RouteDefinition;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import com.fooddelivery.apiflow.validation.JsonSchemaUtil;
import com.fooddelivery.apiflow.web.filter.CachedBodyHttpServletRequest;
import com.fooddelivery.apiflow.web.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

public class ApiFlowInterceptor implements HandlerInterceptor {
    private static final Logger log = LogManager.getLogger(ApiFlowInterceptor.class);

    private final ApiRegistry apiRegistry;
    private final ApiResponseWriter apiResponseWriter;

    public ApiFlowInterceptor(ApiRegistry apiRegistry, ApiResponseWriter apiResponseWriter) {
        this.apiRegistry = apiRegistry;
        this.apiResponseWriter = apiResponseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        RouteDefinition route = apiRegistry.getRoute(method, path);

        // Every rejection writes the error response directly, in the shared ApiResponse format
        if (route == null) {
            log.warn("API not declared in routes config: [{}] {}", method, path);
            reject(request, response, ApiFlowErrorCode.API_NOT_FOUND);
            return false;
        }

        if (!(handler instanceof HandlerMethod)) {
            log.warn("No controller method mapped for declared API: [{}] {}", method, path);
            reject(request, response, ApiFlowErrorCode.API_NOT_FOUND);
            return false;
        }

        ResolvedValidator validator = route.getResolvedValidator();
        boolean hasValidator = validator != null;

        String requestBody = "";
        if (route.getSchema() != null || hasValidator) {
            CachedBodyHttpServletRequest cachedRequest = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
            if (cachedRequest != null) {
                requestBody = cachedRequest.getBodyAsString();
            }
        }

        if (route.getSchema() != null && !JsonSchemaUtil.validate(route.getSchema(), requestBody)) {
            log.warn("Request body does not match schema {}: [{}] {}", route.getSchemaPath(), method, path);
            reject(request, response, ApiFlowErrorCode.INVALID_REQUEST_BODY);
            return false;
        }

        if (hasValidator && !validator.validate(request, requestBody)) {
            log.warn("Validator {} rejected the request: [{}] {}", route.getValidator(), method, path);
            reject(request, response, ApiFlowErrorCode.REQUEST_VALIDATION_FAILED);
            return false;
        }

        return true;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ApiFlowErrorCode error) throws Exception {
        apiResponseWriter.writeError(request, response, error.getStatus(), error.getCode(), error.getMessage());
    }
}
