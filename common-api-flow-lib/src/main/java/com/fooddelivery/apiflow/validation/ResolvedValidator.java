package com.fooddelivery.apiflow.validation;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// A validator method looked up once at startup: public boolean name(HttpServletRequest, String)
public record ResolvedValidator(Object bean, Method method) {

    public boolean validate(HttpServletRequest request, String requestBody) throws Exception {
        try {
            return Boolean.TRUE.equals(method.invoke(bean, request, requestBody));
        } catch (InvocationTargetException e) {
            // Rethrow what the validator itself threw, so it is not hidden behind a reflection exception
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
