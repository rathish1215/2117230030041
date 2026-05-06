package com.affordmed.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom HTTP request/response logging filter.
 * Intercepts every incoming request and logs:
 *  - HTTP method, URI, query params, headers
 *  - Response status code
 *  - Processing duration in milliseconds
 *
 * Uses LoggingService (file-based) — NO built-in loggers.
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private final LoggingService loggingService;

    public LoggingFilter(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // Extract request headers
        Map<String, String> headers = extractHeaders(request);

        try {
            // Continue the filter chain (process the request)
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Build and write the log entry
            LogEntry logEntry = new LogEntry(
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString(),
                    headers,
                    response.getStatus(),
                    duration,
                    request.getRemoteAddr()
            );

            loggingService.log(logEntry);
        }
    }

    /**
     * Extracts all headers from the HTTP request into a Map.
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }
}
