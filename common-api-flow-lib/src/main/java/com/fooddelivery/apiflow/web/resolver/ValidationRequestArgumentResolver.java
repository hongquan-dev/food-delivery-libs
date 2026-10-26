package com.fooddelivery.apiflow.web.resolver;

import com.fooddelivery.apiflow.route.ApiRegistry;
import com.fooddelivery.apiflow.route.RouteDefinition;
import com.fooddelivery.apiflow.validation.ValidationRequest;
import com.fooddelivery.apiflow.web.filter.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Lets a controller method declare a {@link ValidationRequest} parameter, as an alternative to
 * {@code @PathVariable}/{@code @RequestParam}/{@code @RequestHeader}/{@code @RequestBody}:
 *
 * <pre>
 * &#64;PostMapping("/orders/{id}/items")
 * public ResponseEntity&lt;...&gt; addItem(ValidationRequest request) { ... }
 * </pre>
 *
 * Unlike those annotations, a missing value is returned as {@code null}; nothing is rejected automatically.
 */
public class ValidationRequestArgumentResolver implements HandlerMethodArgumentResolver {
    private final ApiRegistry apiRegistry;

    public ValidationRequestArgumentResolver(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ValidationRequest.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        // Route with a validator: reuse the instance ApiFlowInterceptor built and the validator already checked
        if (request.getAttribute(ValidationRequest.ATTRIBUTE) instanceof ValidationRequest validationRequest) {
            return validationRequest;
        }

        // Otherwise build one now and store it, so several ValidationRequest parameters share one instance
        ValidationRequest validationRequest = new ValidationRequest(request, resolvePathVariables(request),
                CachedBodyHttpServletRequest.readCachedBody(request));
        request.setAttribute(ValidationRequest.ATTRIBUTE, validationRequest);
        return validationRequest;
    }

    // Variable names come from <path> in api-routes.xml, so they match what a validator would see.
    // Paths excluded from the API flow have no route; then Spring's own mapping variables are used
    @SuppressWarnings("unchecked")
    private Map<String, String> resolvePathVariables(HttpServletRequest request) {
        String path = request.getRequestURI();
        RouteDefinition route = apiRegistry.getRoute(request.getMethod(), path);
        if (route != null) {
            return route.extractPathVariables(path);
        }
        Object uriVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return uriVariables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}
