package com.fooddelivery.web.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fooddelivery.web.exception.GlobalExceptionHandler;
import com.fooddelivery.web.handler.ApiResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link GlobalExceptionHandler} and {@link ApiResponseWriter} in every Spring MVC service that depends on
 * this library, without component scanning. Listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>A service can replace either bean by declaring its own bean of the same type (e.g. a subclass).
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    // Used by servlet filters and interceptors to write error responses with the same JSON settings as controllers.
    // Falls back to a standalone mapper (ISO dates) if the application has no ObjectMapper bean
    @Bean
    @ConditionalOnMissingBean
    public ApiResponseWriter apiResponseWriter(ObjectProvider<ObjectMapper> objectMapper) {
        return new ApiResponseWriter(objectMapper.getIfAvailable(() -> JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()));
    }
}
