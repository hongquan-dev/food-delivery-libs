package com.fooddelivery.apiflow.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.apiflow.validation.ResolvedValidator;

public class RouteDefinition {
    private final String method;
    private final String path;
    private final String validator;
    private final String schemaPath;
    private final JsonNode schema;
    private final String controllerName;

    private ResolvedValidator resolvedValidator;

    public RouteDefinition(String method, String path, String validator, String schemaPath, JsonNode schema, String controllerName) {
        this.method = method;
        this.path = path;
        this.validator = validator;
        this.schemaPath = schemaPath;
        this.schema = schema;
        this.controllerName = controllerName;
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
