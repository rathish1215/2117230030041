package com.affordmed.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to register the LoggingFilter
 * with the highest precedence so it captures all requests.
 */
@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilterRegistration(LoggingFilter loggingFilter) {
        FilterRegistrationBean<LoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(loggingFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // Highest priority — runs first
        registration.setName("loggingFilter");
        return registration;
    }
}
