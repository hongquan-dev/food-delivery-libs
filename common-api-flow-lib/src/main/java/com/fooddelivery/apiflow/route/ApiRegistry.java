package com.fooddelivery.apiflow.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fooddelivery.apiflow.exception.ApiFlowConfigException;
import com.fooddelivery.apiflow.route.xml.ApiConfigWrapper;
import com.fooddelivery.apiflow.validation.JsonSchemaUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PatternParseException;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory registry of the routes declared in the routes file (default {@code classpath:api-routes.xml}). It is the
 * service's allow list: a request whose method + path is not found here is rejected with 404.
 *
 * <p>The constructor validates every {@code <route>}, preloads its JSON Schema and throws all problems at once as an
 * {@link ApiFlowConfigException}; checks that need the Spring context are left to {@link ApiRouteVerifier}.
 * {@link #getRoute} tries exact paths first, then patterns from most to least specific ({@code @RequestMapping}
 * syntax). Read-only after startup, so safe to share between request threads.
 */
public class ApiRegistry {
    private static final Logger log = LogManager.getLogger(ApiRegistry.class);

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    // Key: "METHOD:/exact/path"
    private final Map<String, RouteDefinition> exactRoutes = new HashMap<>();

    // Key: HTTP method. Each list is sorted from most to least specific pattern
    private final Map<String, List<PatternRoute>> patternRoutes = new HashMap<>();

    // Every valid route in file order; used by ApiRouteVerifier
    private final List<RouteDefinition> allRoutes = new ArrayList<>();

    private final String apiRoutesLocation;

    public ApiRegistry(String apiRoutesLocation) {
        this.apiRoutesLocation = apiRoutesLocation;
        init();
    }

    private void init() {
        ApiConfigWrapper config;
        try (InputStream inputStream = new ClassPathResource(apiRoutesLocation).getInputStream()) {
            config = new XmlMapper().readValue(inputStream, ApiConfigWrapper.class);
        } catch (Exception e) {
            log.error("Failed to read API routes config file '{}': {}", apiRoutesLocation, e.getMessage(), e);
            throw new IllegalStateException("Unable to load API routes config: " + apiRoutesLocation, e);
        }
        // An empty <routes/> is allowed; ApiRouteVerifier will then report every controller endpoint as undeclared
        if (config == null || config.getRoutes() == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        // "METHOD:normalized path" -> path as written, to detect duplicates such as /items/{id} vs /items/{itemId}
        Map<String, String> declared = new HashMap<>();

        for (ApiConfigWrapper.RouteXmlDto dto : config.getRoutes()) {
            String method = dto.getMethod() == null ? "" : dto.getMethod().trim().toUpperCase();
            String path = dto.getPath() == null ? "" : dto.getPath().trim();
            String label = "[" + method + "] " + path;

            // Each check below records an error and skips the route, so one run reports every broken route
            if (!HTTP_METHODS.contains(method)) {
                errors.add(label + ": invalid <method> '" + dto.getMethod() + "', expected one of " + HTTP_METHODS);
                continue;
            }
            if (!path.startsWith("/")) {
                errors.add(label + ": <path> must not be empty and must start with '/'");
                continue;
            }

            PathPattern pattern;
            try {
                pattern = PATH_PATTERN_PARSER.parse(path);
            } catch (PatternParseException e) {
                errors.add(label + ": invalid <path> pattern: " + e.getMessage());
                continue;
            }

            String previous = declared.putIfAbsent(method + ":" + normalizePattern(path), path);
            if (previous != null) {
                errors.add(label + ": duplicate route" + (previous.equals(path) ? "" : " (same as " + previous + ")"));
                continue;
            }

            // <schema> is optional; an empty tag means "no body structure check"
            String schemaPath = dto.getSchema();
            JsonNode schemaNode = null;
            if (schemaPath != null && !schemaPath.trim().isEmpty()) {
                schemaPath = schemaPath.trim();
                try {
                    schemaNode = JsonSchemaUtil.loadSchema(schemaPath);
                } catch (Exception e) {
                    errors.add(label + ": cannot load <schema> '" + schemaPath + "': " + e.getMessage());
                    continue;
                }
            } else {
                schemaPath = null;
            }

            // <validator> is kept as text here; ApiRouteVerifier resolves it to a bean + method once the context is ready
            String validator = dto.getValidator() == null || dto.getValidator().isBlank() ? null : dto.getValidator().trim();
            // <controller> is optional; when present, ApiRouteVerifier checks that this controller really handles the route
            String controller = dto.getController() == null || dto.getController().isBlank() ? null : dto.getController().trim();
            RouteDefinition route = new RouteDefinition(method, path, pattern, validator, schemaPath, schemaNode, controller);
            allRoutes.add(route);

            if (pattern.hasPatternSyntax()) {
                patternRoutes.computeIfAbsent(method, k -> new ArrayList<>()).add(new PatternRoute(pattern, route));
            } else {
                exactRoutes.put(method + ":" + path, route);
            }
        }
        patternRoutes.values().forEach(list ->
                list.sort((a, b) -> PathPattern.SPECIFICITY_COMPARATOR.compare(a.pattern(), b.pattern())));

        if (!errors.isEmpty()) {
            throw new ApiFlowConfigException(apiRoutesLocation, errors);
        }
    }

    /**
     * Finds the route declared for a request.
     *
     * @param method HTTP method of the request (any case)
     * @param path   request URI without query string, e.g. {@code /orders/7/items}
     * @return the matching route, or {@code null} if the API is not declared
     */
    public RouteDefinition getRoute(String method, String path) {
        String upperMethod = method.toUpperCase();
        RouteDefinition route = exactRoutes.get(upperMethod + ":" + path);
        if (route != null) {
            return route;
        }

        PathContainer pathContainer = PathContainer.parsePath(path);
        for (PatternRoute patternRoute : patternRoutes.getOrDefault(upperMethod, List.of())) {
            if (patternRoute.pattern().matches(pathContainer)) {
                return patternRoute.route();
            }
        }
        return null;
    }

    public List<RouteDefinition> getRoutes() {
        return Collections.unmodifiableList(allRoutes);
    }

    public String getApiRoutesLocation() {
        return apiRoutesLocation;
    }

    // Replaces simple variables with "{}" so paths that differ only by variable name compare equal:
    // /orders/{id} and /orders/{orderId} -> /orders/{}. Variables with a regex ({id:\d+}) and
    // capture-all variables ({*rest}) are left as they are
    static String normalizePattern(String path) {
        return path.replaceAll("\\{(?!\\*)[^}:]+}", "{}");
    }

    private record PatternRoute(PathPattern pattern, RouteDefinition route) {}
}
