package com.fooddelivery.apiflow.exception;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

// Turns ApiFlowConfigException into Spring Boot's "APPLICATION FAILED TO START" report instead of a long stack trace.
// Registered in META-INF/spring.factories.
public class ApiFlowConfigFailureAnalyzer extends AbstractFailureAnalyzer<ApiFlowConfigException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ApiFlowConfigException cause) {
        String description = "The API routes config '" + cause.getRoutesLocation() + "' has "
                + cause.getErrors().size() + " error(s):\n\n    - " + String.join("\n    - ", cause.getErrors());
        String action = "Fix the routes above in '" + cause.getRoutesLocation() + "' (or the controllers/validators they point to)."
                + " See the README of common-api-flow-lib for the expected format.";
        return new FailureAnalysis(description, action, cause);
    }
}
