package com.fooddelivery.apiflow.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fooddelivery.apiflow.route.ApiRegistry;
import com.fooddelivery.apiflow.route.ApiRouteVerifier;
import com.fooddelivery.apiflow.web.filter.RequestCachingFilter;
import com.fooddelivery.apiflow.web.interceptor.ApiFlowInterceptor;
import com.fooddelivery.apiflow.web.resolver.ValidationRequestArgumentResolver;
import com.fooddelivery.apiflow.web.response.ApiResponseWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// After JacksonAutoConfiguration, so ApiResponseWriter uses Spring Boot's ObjectMapper
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ApiFlowProperties.class)
public class ApiFlowAutoConfiguration {
    private static final Logger log = LogManager.getLogger(ApiFlowAutoConfiguration.class);

    // Writes error responses from the filter and the interceptor with the same JSON settings as controllers
    @Bean
    @ConditionalOnMissingBean
    public ApiResponseWriter apiResponseWriter(ObjectProvider<ObjectMapper> objectMapper) {
        return new ApiResponseWriter(objectMapper.getIfAvailable(() -> JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()));
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiRegistry apiRegistry(ApiFlowProperties properties) {
        return new ApiRegistry(properties.getRoutesLocation());
    }

    @Bean
    @ConditionalOnMissingBean(name = "requestCachingFilter")
    public FilterRegistrationBean<RequestCachingFilter> requestCachingFilter(ApiFlowProperties properties,
                                                                            ApiResponseWriter apiResponseWriter) {
        FilterRegistrationBean<RequestCachingFilter> registration = new FilterRegistrationBean<>(
                new RequestCachingFilter(properties.getMaxBodySize().toBytes(), apiResponseWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public ApiRouteVerifier apiRouteVerifier(ApiRegistry apiRegistry, ApplicationContext applicationContext,
                                             ApiFlowProperties properties) {
        return new ApiRouteVerifier(apiRegistry, applicationContext, properties.resolveExcludePaths());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiFlowInterceptor apiFlowInterceptor(ApiRegistry apiRegistry, ApiResponseWriter apiResponseWriter) {
        return new ApiFlowInterceptor(apiRegistry, apiResponseWriter);
    }

    @Bean
    public WebMvcConfigurer apiFlowWebMvcConfigurer(ApiFlowInterceptor apiFlowInterceptor, ApiFlowProperties properties,
                                                    ApiRegistry apiRegistry) {
        List<String> excludePaths = properties.resolveExcludePaths();
        log.info("API flow excluded paths: {}", excludePaths);

        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(apiFlowInterceptor).excludePathPatterns(excludePaths);
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new ValidationRequestArgumentResolver(apiRegistry));
            }
        };
    }
}
