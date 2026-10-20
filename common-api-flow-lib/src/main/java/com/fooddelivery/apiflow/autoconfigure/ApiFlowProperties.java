package com.fooddelivery.apiflow.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "api-flow")
public class ApiFlowProperties {

    public static final List<String> DEFAULT_EXCLUDE_PATHS = List.of(
            "/actuator/**",       
            "/v3/api-docs/**",   
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    );

    private String routesLocation = "api-routes.xml";

    private DataSize maxBodySize = DataSize.ofMegabytes(1);

    private List<String> excludePaths = new ArrayList<>();

    private boolean useDefaultExcludes = true;

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
