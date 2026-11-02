package com.fooddelivery.apiflow.route;

import com.fooddelivery.apiflow.exception.ApiFlowConfigException;
import com.fooddelivery.apiflow.request.ApiRequest;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Startup check, run once after all singletons are created, that keeps api-routes.xml and the controllers in sync:
 * every route must map to a controller method (in the class named by {@code <controller>}, when given), every
 * application endpoint must be declared or excluded, and every
 * {@code <validator>} must resolve to a bean method {@code public boolean name(ApiRequest)}, which is then
 * cached on the route as a {@link ResolvedValidator}.
 *
 * <p>All problems are thrown together as one {@link ApiFlowConfigException}. Paths are compared after
 * {@link ApiRegistry#normalizePattern}, so {@code /orders/{id}} matches {@code @GetMapping("/orders/{orderId}")}.
 */
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
        // All @RequestMapping endpoints known to Spring MVC
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = applicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                .getHandlerMethods();

        Set<Class<?>> controllerClasses = findControllerClasses();

        List<String> errors = new ArrayList<>();
        for (RouteDefinition route : apiRegistry.getRoutes()) {
            String label = "[" + route.getMethod() + "] " + route.getPath();
            Set<Class<?>> mappedControllers = findMappedControllers(route, handlerMethods);
            if (mappedControllers.isEmpty()) {
                errors.add(label + ": no controller method is mapped to this method and path");
            } else if (route.getControllerName() != null) {
                verifyController(route, label, mappedControllers, controllerClasses, errors);
            }
            if (route.getValidator() != null) {
                resolveValidator(route, label, errors);
            }
        }

        collectUndeclaredEndpointErrors(handlerMethods, errors);

        if (!errors.isEmpty()) {
            throw new ApiFlowConfigException(apiRegistry.getApiRoutesLocation(), errors);
        }

        log.info("API flow: {} routes verified against controllers", apiRegistry.getRoutes().size());
    }

    // Turns "com.example.FooValidator.validateBar" into a callable bean + method and stores it on the route.
    // Any problem is added to errors instead of thrown, so all routes are checked in one run
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
            clazz = ClassUtils.forName(className, applicationContext.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            errors.add(label + ": validator class not found: " + className);
            return;
        }

        // The validator must be exactly one Spring bean, so it can use injected services (repositories, clients...)
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

        // Looked up on the bean's runtime class, which may be a proxy subclass (e.g. @Transactional, AOP)
        Method method;
        try {
            method = bean.getClass().getMethod(methodName, ApiRequest.class);
        } catch (NoSuchMethodException e) {
            errors.add(label + ": validator method not found: public boolean " + methodName
                    + "(ApiRequest request) in " + className);
            return;
        }
        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            errors.add(label + ": validator method " + className + "." + methodName + " must return boolean");
            return;
        }

        route.setResolvedValidator(new ResolvedValidator(bean, method));
    }

    // Declared route -> controller: classes of the mappings with a compatible HTTP method and the same (normalized)
    // path; empty set if no controller handles the route
    private Set<Class<?>> findMappedControllers(RouteDefinition route,
                                                Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        String normalizedPath = ApiRegistry.normalizePattern(route.getPath());
        Set<Class<?>> controllers = new LinkedHashSet<>();
        handlerMethods.forEach((info, handlerMethod) -> {
            if (supportsMethod(info, route.getMethod())
                    && info.getPatternValues().stream().anyMatch(p -> ApiRegistry.normalizePattern(p).equals(normalizedPath))) {
                controllers.add(handlerMethod.getBeanType());
            }
        });
        return controllers;
    }

    // <controller> must name a controller of the application, and that controller must be the one handling the route.
    // Any problem is added to errors instead of thrown, so all routes are checked in one run
    private static void verifyController(RouteDefinition route, String label, Set<Class<?>> mappedControllers,
                                         Set<Class<?>> controllerClasses, List<String> errors) {
        String name = route.getControllerName();
        if (mappedControllers.stream().anyMatch(clazz -> matchesName(clazz, name))) {
            return;
        }
        String handledBy = mappedControllers.stream().map(Class::getName).toList().toString();
        if (controllerClasses.stream().noneMatch(clazz -> matchesName(clazz, name))) {
            errors.add(label + ": <controller> '" + name + "' is not a controller class (no @Controller/@RestController"
                    + " bean with this simple or full class name); this route is handled by " + handledBy);
        } else {
            errors.add(label + ": <controller> '" + name + "' does not handle this method and path; it is handled by "
                    + handledBy);
        }
    }

    // Application controllers (@Controller, including @RestController), with or without mappings, e.g. to tell a
    // misspelled <controller> apart from one that exists but does not handle the route
    private Set<Class<?>> findControllerClasses() {
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String beanName : applicationContext.getBeanNamesForAnnotation(Controller.class)) {
            Class<?> type = applicationContext.getType(beanName);
            if (type != null) {
                classes.add(ClassUtils.getUserClass(type));
            }
        }
        return classes;
    }

    // "OrderController" or "com.fooddelivery.order.controller.OrderController" both match OrderController
    private static boolean matchesName(Class<?> clazz, String name) {
        return clazz.getSimpleName().equals(name) || clazz.getName().equals(name);
    }

    // Controller -> declared route: reports every application endpoint that is neither declared nor excluded
    private void collectUndeclaredEndpointErrors(Map<RequestMappingInfo, HandlerMethod> handlerMethods,
                                                 List<String> errors) {
        handlerMethods.forEach((info, handlerMethod) -> {
            if (!isApplicationController(handlerMethod)) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (isExcluded(pattern) || isDeclared(pattern, methods)) {
                    continue;
                }
                errors.add((methods.isEmpty() ? "[ANY]" : methods.toString()) + " " + pattern
                        + ": controller endpoint " + handlerMethod.getBeanType().getSimpleName() + "#"
                        + handlerMethod.getMethod().getName() + " is not declared in "
                        + apiRegistry.getApiRoutesLocation());
            }
        });
    }

    // A mapping without an HTTP method (plain @RequestMapping) accepts any method, so one declared route is enough
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

    // Skips framework controllers (e.g. Spring Boot's BasicErrorController, springdoc's OpenAPI endpoints)
    private static boolean isApplicationController(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();
        return !packageName.startsWith("org.springframework.") && !packageName.startsWith("org.springdoc.");
    }

    // A HEAD route is served by a GET mapping, as Spring MVC handles HEAD implicitly for GET endpoints
    private static boolean supportsMethod(RequestMappingInfo info, String method) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty()
                || methods.stream().anyMatch(m -> m.name().equals(method))
                || ("HEAD".equals(method) && methods.contains(RequestMethod.GET));
    }
}
