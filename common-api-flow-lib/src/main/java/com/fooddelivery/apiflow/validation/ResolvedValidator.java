package com.fooddelivery.apiflow.validation;

import com.fooddelivery.apiflow.request.ApiRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A route's business validator, resolved once at startup by {@code ApiRouteVerifier} from the
 * {@code <validator>} text in api-routes.xml: the Spring bean plus its {@code boolean name(ApiRequest)} method.
 *
 * @param bean   the validator bean (may be a Spring proxy)
 * @param method the public validation method, taking a single {@link ApiRequest}
 */
public record ResolvedValidator(Object bean, Method method) {

    /**
     * Calls the validator. A {@code null} result from a {@code Boolean} method counts as {@code false}.
     *
     * <p>An exception thrown by the validator is rethrown as is (not wrapped in an
     * {@link InvocationTargetException}), so the service's {@code @ExceptionHandler}/{@code @ControllerAdvice}
     * sees the original exception type. Without such a handler, Spring answers 500.
     */
    public boolean validate(ApiRequest request) throws Exception {
        try {
            return Boolean.TRUE.equals(method.invoke(bean, request));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
