package com.fooddelivery.apiflow.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.apiflow.registry.ApiRegistry;
import com.fooddelivery.apiflow.registry.ResolvedValidator;
import com.fooddelivery.apiflow.schema.JsonSchemaUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.Map;

public class ApiFlowInterceptor implements HandlerInterceptor {
    private static final Logger log = LogManager.getLogger(ApiFlowInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ApiRegistry apiRegistry;

    public ApiFlowInterceptor(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        ApiRegistry.RouteDefinition route = apiRegistry.getRoute(method, path);

        if (route == null) {
            log.warn("API not declared in routes config: [{}] {}", method, path);
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "API not found in system configuration!"));
            return false;
        }

        if (!(handler instanceof HandlerMethod)) {
            log.warn("No controller method mapped for declared API: [{}] {}", method, path);
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "API not found in controller!"));
            return false;
        }

        // Resolved once at startup by ApiRouteVerifier
        ResolvedValidator validator = route.getResolvedValidator();
        boolean hasValidator = validator != null;

        // Only the body cached by RequestCachingFilter is used (JSON, size-limited). Never read the raw stream here:
        // a non-JSON body is not size-limited and would also be consumed before the controller sees it.
        String requestBody = "";
        if (route.getSchema() != null || hasValidator) {
            CachedBodyHttpServletRequest cachedRequest = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
            if (cachedRequest != null) {
                requestBody = cachedRequest.getBodyAsString();
            }
        }

        if (route.getSchema() != null && !JsonSchemaUtil.validate(route.getSchema(), requestBody)) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Invalid request body!"));
            return false;
        }

        if (hasValidator && !validator.validate(request, requestBody)) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Request data failed validation!"));
            return false;
        }

        return true;
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
