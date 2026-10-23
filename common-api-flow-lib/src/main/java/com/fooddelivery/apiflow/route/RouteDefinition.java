package com.fooddelivery.apiflow.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;

public class RouteDefinition {
    private final String method;
    private final String path;
    private final PathPattern pathPattern;
    private final String validator;
    private final String schemaPath;
    private final JsonNode schema;
    private final String controllerName;

    private ResolvedValidator resolvedValidator;

    public RouteDefinition(String method, String path, PathPattern pathPattern, String validator, String schemaPath,
                           JsonNode schema, String controllerName) {
        this.method = method;
        this.path = path;
        this.pathPattern = pathPattern;
        this.validator = validator;
        this.schemaPath = schemaPath;
        this.schema = schema;
        this.controllerName = controllerName;
    }

    // Values of the variables in <path> (e.g. {id}) for a request path that matched this route
    public Map<String, String> extractPathVariables(String requestPath) {
        PathPattern.PathMatchInfo matchInfo = pathPattern.matchAndExtract(PathContainer.parsePath(requestPath));
        return matchInfo == null ? Map.of() : matchInfo.getUriVariables();
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getValidator() { return validator; }
    public String getSchemaPath() { return schemaPath; }
    public JsonNode getSchema() { return schema; }
    public String getControllerName() { return controllerName; }
    public ResolvedValidator getResolvedValidator() { return resolvedValidator; }
    void setResolvedValidator(ResolvedValidator resolvedValidator) { this.resolvedValidator = resolvedValidator; }
}
