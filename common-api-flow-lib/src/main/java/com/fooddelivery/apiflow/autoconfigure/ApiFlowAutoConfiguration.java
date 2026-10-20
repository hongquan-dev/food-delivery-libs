package com.fooddelivery.apiflow.autoconfigure;

import com.fooddelivery.apiflow.route.ApiRegistry;
import com.fooddelivery.apiflow.route.ApiRouteVerifier;
import com.fooddelivery.apiflow.web.interceptor.ApiFlowInterceptor;
import com.fooddelivery.apiflow.web.filter.RequestCachingFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ApiFlowProperties.class)
public class ApiFlowAutoConfiguration {
    private static final Logger log = LogManager.getLogger(ApiFlowAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ApiRegistry apiRegistry(ApiFlowProperties properties) {
        return new ApiRegistry(properties.getRoutesLocation());
    }

    @Bean
    @ConditionalOnMissingBean(name = "requestCachingFilter")
    public FilterRegistrationBean<RequestCachingFilter> requestCachingFilter(ApiFlowProperties properties) {
        FilterRegistrationBean<RequestCachingFilter> registration =
                new FilterRegistrationBean<>(new RequestCachingFilter(properties.getMaxBodySize().toBytes()));
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
    public ApiFlowInterceptor apiFlowInterceptor(ApiRegistry apiRegistry) {
        return new ApiFlowInterceptor(apiRegistry);
    }

    @Bean
    public WebMvcConfigurer apiFlowWebMvcConfigurer(ApiFlowInterceptor apiFlowInterceptor, ApiFlowProperties properties) {
        List<String> excludePaths = properties.resolveExcludePaths();
        log.info("API flow excluded paths: {}", excludePaths);

        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(apiFlowInterceptor).excludePathPatterns(excludePaths);
            }
        };
    }
}
