package com.fooddelivery.apiflow.exception;

import java.util.List;

public class ApiFlowConfigException extends IllegalStateException {
    private final String routesLocation;
    private final List<String> errors;

    public ApiFlowConfigException(String routesLocation, List<String> errors) {
        super("Invalid API routes config '" + routesLocation + "':\n - " + String.join("\n - ", errors));
        this.routesLocation = routesLocation;
        this.errors = List.copyOf(errors);
    }

    public String getRoutesLocation() { return routesLocation; }
    public List<String> getErrors() { return errors; }
}
