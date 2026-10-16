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

// Loads api-routes.xml at startup. Every problem in the file (duplicate route, bad method/path, unreadable schema)
// is collected and reported at once, and the service refuses to start.
public class ApiRegistry {
    private static final Logger log = LogManager.getLogger(ApiRegistry.class);

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    // Routes without variables, looked up directly by "METHOD:path"
    private final Map<String, RouteDefinition> exactRoutes = new HashMap<>();

    // Routes with variables (e.g. /orders/{id}), grouped by method and sorted most specific first,
    // so /orders/new wins over /orders/{id} — same rule as Spring MVC
    private final Map<String, List<PatternRoute>> patternRoutes = new HashMap<>();

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
        if (config == null || config.getRoutes() == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        // "METHOD:normalized path" -> path as written, to report duplicates like /items/{id} and /items/{itemId}
        Map<String, String> declared = new HashMap<>();

        for (ApiConfigWrapper.RouteXmlDto dto : config.getRoutes()) {
            String method = dto.getMethod() == null ? "" : dto.getMethod().trim().toUpperCase();
            String path = dto.getPath() == null ? "" : dto.getPath().trim();
            String label = "[" + method + "] " + path;

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

            String validator = dto.getValidator() == null || dto.getValidator().isBlank() ? null : dto.getValidator().trim();
            RouteDefinition route = new RouteDefinition(method, path, validator, schemaPath, schemaNode, dto.getController());
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

    // Variable names do not change what a path matches: /items/{id} and /items/{itemId} are the same route.
    // Regex variables ({id:\d+}) and catch-all variables ({*path}) are kept as written.
    static String normalizePattern(String path) {
        return path.replaceAll("\\{(?!\\*)[^}:]+}", "{}");
    }

    private record PatternRoute(PathPattern pattern, RouteDefinition route) {}
}
