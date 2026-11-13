package com.fooddelivery.apiflow.validation;

import com.fooddelivery.apiflow.request.ApiRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A route's business validator, resolved once at startup by {@code ApiRouteVerifier} from the
 * {@code <validator>} text in api-routes.xml: the Spring bean plus its {@code void name(ApiRequest)} or
 * {@code boolean name(ApiRequest)} method.
 *
 * @param bean   the validator bean (may be a Spring proxy)
 * @param method the public validation method, taking a single {@link ApiRequest}
 */
public record ResolvedValidator(Object bean, Method method) {

    /**
     * Calls the validator. A {@code void} method passes unless it throws; a {@code Boolean} method returning
     * {@code null} counts as {@code false}.
     *
     * <p>An exception thrown by the validator is rethrown as is (not wrapped in an
     * {@link InvocationTargetException}), so a {@code BusinessException} reaches {@code GlobalExceptionHandler}
     * (common-web-lib) with its own status, code and field errors.
     */
    public boolean validate(ApiRequest request) throws Exception {
        try {
            Object result = method.invoke(bean, request);
            return method.getReturnType() == void.class || Boolean.TRUE.equals(result);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
