package com.fooddelivery.apiflow.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

// Settings of common-api-flow-lib, configured by each service with the "api-flow." prefix
@ConfigurationProperties(prefix = "api-flow")
public class ApiFlowProperties {

    // System endpoints that are not business APIs and must never be declared in api-routes.xml
    public static final List<String> DEFAULT_EXCLUDE_PATHS = List.of(
            "/actuator/**",       // Spring Boot Actuator (health check, metrics)
            "/v3/api-docs/**",    // OpenAPI docs (springdoc)
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    );

    // Classpath location of the routes file
    private String routesLocation = "api-routes.xml";

    // Max JSON body size cached by RequestCachingFilter, larger bodies get 413
    private DataSize maxBodySize = DataSize.ofMegabytes(1);

    // Extra paths (Spring path patterns, e.g. /webhook/**) that skip the API flow entirely
    private List<String> excludePaths = new ArrayList<>();

    // Set to false to drop DEFAULT_EXCLUDE_PATHS (/error is always excluded)
    private boolean useDefaultExcludes = true;

    // Final list: /error (always) + DEFAULT_EXCLUDE_PATHS (if enabled) + excludePaths, without duplicates
    public List<String> resolveExcludePaths() {
        List<String> paths = new ArrayList<>();
        // /error must stay reachable, otherwise every forwarded error would become a 404
        paths.add("/error");
        if (useDefaultExcludes) {
            paths.addAll(DEFAULT_EXCLUDE_PATHS);
        }
        for (String path : excludePaths) {
            if (path != null && !path.isBlank() && !paths.contains(path.trim())) {
                paths.add(path.trim());
            }
        }
        return paths;
    }

    public String getRoutesLocation() { return routesLocation; }
    public void setRoutesLocation(String routesLocation) { this.routesLocation = routesLocation; }
    public DataSize getMaxBodySize() { return maxBodySize; }
    public void setMaxBodySize(DataSize maxBodySize) { this.maxBodySize = maxBodySize; }
    public List<String> getExcludePaths() { return excludePaths; }
    public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    public boolean isUseDefaultExcludes() { return useDefaultExcludes; }
    public void setUseDefaultExcludes(boolean useDefaultExcludes) { this.useDefaultExcludes = useDefaultExcludes; }
}
