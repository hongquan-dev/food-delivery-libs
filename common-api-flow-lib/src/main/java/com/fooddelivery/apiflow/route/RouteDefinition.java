package com.fooddelivery.apiflow.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.apiflow.validation.ResolvedValidator;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;

/**
 * One {@code <route>} of api-routes.xml after parsing and validation, as used at request time.
 *
 * <p>Created by {@link ApiRegistry}. The validator is filled in later by {@link ApiRouteVerifier} at startup
 * ({@link #getResolvedValidator()} stays {@code null} for routes without a {@code <validator>}).
 */
public class RouteDefinition {
    // Upper-case HTTP method, e.g. "POST"
    private final String method;
    // Path as written in the XML, e.g. "/orders/{id}/items"
    private final String path;
    private final PathPattern pathPattern;
    // Raw "<full class name>.<method name>" from the XML, null if none
    private final String validator;
    // Classpath location of the JSON Schema, null if none
    private final String schemaPath;
    // Parsed JSON Schema, loaded once at startup; null if none
    private final JsonNode schema;
    // Informational only (<controller> in the XML); matching to controllers is done by method + path
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

    // Variables of <path> with their values in the request, e.g. /orders/{id} + /orders/7 -> {id=7}.
    // Names come from the XML (not from the controller mapping); empty map if the path does not match
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
    // Package-private: only ApiRouteVerifier sets it, once, during startup
    void setResolvedValidator(ResolvedValidator resolvedValidator) { this.resolvedValidator = resolvedValidator; }
}
