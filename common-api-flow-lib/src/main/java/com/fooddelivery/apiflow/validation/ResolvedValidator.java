package com.fooddelivery.apiflow.validation;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record ResolvedValidator(Object bean, Method method) {

    public boolean validate(HttpServletRequest request, String requestBody) throws Exception {
        try {
            return Boolean.TRUE.equals(method.invoke(bean, request, requestBody));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
