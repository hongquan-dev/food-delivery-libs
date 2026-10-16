package com.fooddelivery.apiflow.registry;

import java.util.List;

// Thrown at startup when api-routes.xml is invalid; carries every problem found so they can be fixed in one go
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
