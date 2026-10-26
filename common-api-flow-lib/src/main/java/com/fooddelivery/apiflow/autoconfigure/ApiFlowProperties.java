package com.fooddelivery.apiflow.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings of the library, bound from {@code api-flow.*} in {@code application.properties}/{@code .yml}.
 * Every property is optional.
 *
 * <pre>
 * api-flow.routes-location=api-routes.xml          # classpath location of the routes file
 * api-flow.max-body-size=1MB                       # larger JSON bodies are rejected with 413
 * api-flow.exclude-paths=/auth/.well-known/**      # extra paths that skip the whole API flow
 * api-flow.use-default-excludes=true               # false = drop DEFAULT_EXCLUDE_PATHS
 * </pre>
 */
@ConfigurationProperties(prefix = "api-flow")
public class ApiFlowProperties {

    // Framework endpoints (actuator, OpenAPI docs, Swagger UI, favicon) that are not business APIs,
    // so they are not expected in api-routes.xml
    public static final List<String> DEFAULT_EXCLUDE_PATHS = List.of(
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    );

    private String routesLocation = "api-routes.xml";

    private DataSize maxBodySize = DataSize.ofMegabytes(1);

    // Spring path patterns: "/abc/**" matches /abc and everything below it, "/abc/*" only one level below
    private List<String> excludePaths = new ArrayList<>();

    private boolean useDefaultExcludes = true;

    /**
     * Final list of paths that bypass the API flow (no route lookup, schema or validator check, and no need to be
     * declared in the routes file): {@code /error} + {@link #DEFAULT_EXCLUDE_PATHS} (if enabled) + {@code exclude-paths}.
     *
     * <p>{@code /error} is always included: Spring forwards failed requests there, and checking it against the routes
     * file would turn every error into a 404.
     */
    public List<String> resolveExcludePaths() {
        List<String> paths = new ArrayList<>();
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
