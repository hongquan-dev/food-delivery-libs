package com.fooddelivery.apiflow.route;

import com.fooddelivery.apiflow.exception.ApiFlowConfigException;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.PathContainer;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Runs once all beans are created and before the server accepts requests:
//  - resolves every <validator> to a bean + method, so nothing is looked up per request
//  - checks that every route in api-routes.xml has a matching controller mapping
// Any error stops the startup. Controller endpoints missing from api-routes.xml only log a warning,
// because endpoints under the excluded paths are allowed to be undeclared.
public class ApiRouteVerifier implements SmartInitializingSingleton {
    private static final Logger log = LogManager.getLogger(ApiRouteVerifier.class);

    private final ApiRegistry apiRegistry;
    private final ApplicationContext applicationContext;
    private final List<PathPattern> excludePatterns;

    public ApiRouteVerifier(ApiRegistry apiRegistry, ApplicationContext applicationContext, List<String> excludePaths) {
        this.apiRegistry = apiRegistry;
        this.applicationContext = applicationContext;
        this.excludePatterns = excludePaths.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = applicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                .getHandlerMethods();

        List<String> errors = new ArrayList<>();
        for (RouteDefinition route : apiRegistry.getRoutes()) {
            String label = "[" + route.getMethod() + "] " + route.getPath();
            if (!hasControllerMapping(route, handlerMethods)) {
                errors.add(label + ": no controller method is mapped to this method and path");
            }
            if (route.getValidator() != null) {
                resolveValidator(route, label, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new ApiFlowConfigException(apiRegistry.getApiRoutesLocation(), errors);
        }

        warnUndeclaredEndpoints(handlerMethods);
        log.info("API flow: {} routes verified against controllers", apiRegistry.getRoutes().size());
    }

    private void resolveValidator(RouteDefinition route, String label, List<String> errors) {
        String config = route.getValidator();
        int lastDotIndex = config.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == config.length() - 1) {
            errors.add(label + ": <validator> '" + config + "' must be <full class name>.<method name>");
            return;
        }
        String className = config.substring(0, lastDotIndex);
        String methodName = config.substring(lastDotIndex + 1);

        Class<?> clazz;
        try {
            // Validator classes live in the service, so load them with the service's class loader
            clazz = ClassUtils.forName(className, applicationContext.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            errors.add(label + ": validator class not found: " + className);
            return;
        }

        Object bean;
        try {
            bean = applicationContext.getBean(clazz);
        } catch (NoUniqueBeanDefinitionException e) {
            errors.add(label + ": more than one bean of validator class " + className);
            return;
        } catch (NoSuchBeanDefinitionException e) {
            errors.add(label + ": validator class " + className + " is not a Spring bean (missing @Component, or outside the scanned packages?)");
            return;
        }

        Method method;
        try {
            method = bean.getClass().getMethod(methodName, HttpServletRequest.class, String.class);
        } catch (NoSuchMethodException e) {
            errors.add(label + ": validator method not found: public boolean " + methodName
                    + "(HttpServletRequest request, String requestBody) in " + className);
            return;
        }
        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            errors.add(label + ": validator method " + className + "." + methodName + " must return boolean");
            return;
        }

        route.setResolvedValidator(new ResolvedValidator(bean, method));
    }

    private boolean hasControllerMapping(RouteDefinition route, Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        String normalizedPath = ApiRegistry.normalizePattern(route.getPath());
        for (RequestMappingInfo info : handlerMethods.keySet()) {
            if (supportsMethod(info, route.getMethod())
                    && info.getPatternValues().stream().anyMatch(p -> ApiRegistry.normalizePattern(p).equals(normalizedPath))) {
                return true;
            }
        }
        return false;
    }

    private void warnUndeclaredEndpoints(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        handlerMethods.forEach((info, handlerMethod) -> {
            // Endpoints of Spring itself or of other libraries (e.g. /error) are not business APIs
            if (!isApplicationController(handlerMethod)) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (isExcluded(pattern) || isDeclared(pattern, methods)) {
                    continue;
                }
                log.warn("Controller endpoint {} {} ({}#{}) is not declared in {}, its requests will get 404",
                        methods.isEmpty() ? "[ANY]" : methods, pattern,
                        handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName(),
                        apiRegistry.getApiRoutesLocation());
            }
        });
    }

    private boolean isDeclared(String pattern, Set<RequestMethod> methods) {
        String normalizedPattern = ApiRegistry.normalizePattern(pattern);
        return apiRegistry.getRoutes().stream().anyMatch(route ->
                ApiRegistry.normalizePattern(route.getPath()).equals(normalizedPattern)
                        && (methods.isEmpty() || methods.stream().anyMatch(m -> m.name().equals(route.getMethod()))));
    }

    private boolean isExcluded(String pattern) {
        PathContainer path = PathContainer.parsePath(pattern);
        return excludePatterns.stream().anyMatch(exclude -> exclude.matches(path));
    }

    private static boolean isApplicationController(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();
        return !packageName.startsWith("org.springframework.") && !packageName.startsWith("org.springdoc.");
    }

    // No method condition means the controller accepts every method; HEAD is served by GET mappings in Spring MVC
    private static boolean supportsMethod(RequestMappingInfo info, String method) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty()
                || methods.stream().anyMatch(m -> m.name().equals(method))
                || ("HEAD".equals(method) && methods.contains(RequestMethod.GET));
    }
}
