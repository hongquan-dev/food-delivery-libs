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

// Lets a controller method declare a ValidationRequest parameter instead of @PathVariable/@RequestParam/@RequestHeader/@RequestBody
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

        // Route with a validator: reuse the instance the validator already checked
        if (request.getAttribute(ValidationRequest.ATTRIBUTE) instanceof ValidationRequest validationRequest) {
            return validationRequest;
        }

        ValidationRequest validationRequest = new ValidationRequest(request, resolvePathVariables(request),
                CachedBodyHttpServletRequest.readCachedBody(request));
        request.setAttribute(ValidationRequest.ATTRIBUTE, validationRequest);
        return validationRequest;
    }

    // Names from <path> in api-routes.xml, or from the controller mapping for paths excluded from the API flow
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
