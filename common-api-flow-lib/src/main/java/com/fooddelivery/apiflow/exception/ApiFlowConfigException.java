package com.fooddelivery.apiflow.exception;

import java.util.List;

/**
 * Thrown at startup by {@code ApiRegistry} or {@code ApiRouteVerifier} when the routes file is invalid, so the
 * application refuses to start. Carries every problem found in one pass; displayed by
 * {@link ApiFlowConfigFailureAnalyzer}.
 */
public class ApiFlowConfigException extends IllegalStateException {
    private final String routesLocation;
    private final List<String> errors;

    public ApiFlowConfigException(String routesLocation, List<String> errors) {
        super("Invalid API routes config '" + routesLocation + "':\n - " + String.join("\n - ", errors));
        this.routesLocation = routesLocation;
        this.errors = List.copyOf(errors);
    }

    public String getRoutesLocation() { return routesLocation; }

    // One human-readable message per problem, each prefixed with the route label, e.g. "[POST] /orders: ..."
    public List<String> getErrors() { return errors; }
}
