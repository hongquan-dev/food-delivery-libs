package com.fooddelivery.apiflow.validation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record ResolvedValidator(Object bean, Method method) {

    public boolean validate(ValidationRequest request) throws Exception {
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
