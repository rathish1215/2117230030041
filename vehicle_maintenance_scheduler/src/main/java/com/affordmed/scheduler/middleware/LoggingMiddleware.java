package com.affordmed.scheduler.middleware;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom Logging Middleware — replicates the logging_middleware module.
 * Uses file-based logging only. NO SLF4J, NO Log4j, NO System.out.println.
 */
public class LoggingMiddleware {

    /**
     * Custom file-based logger — writes to logs/app.log
     */
    @Service
    public static class AppLogger {

        private static final String LOG_DIR = "logs";
        private static final String LOG_FILE = "logs/app.log";
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private PrintWriter writer;

        public AppLogger() {
            try {
                File dir = new File(LOG_DIR);
                if (!dir.exists()) dir.mkdirs();
                this.writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)), true);
            } catch (IOException e) {
                throw new RuntimeException("Failed to init log file", e);
            }
        }

        public synchronized void log(String message) {
            if (writer != null) {
                writer.println("[" + LocalDateTime.now().format(FMT) + "] " + message);
                writer.flush();
            }
        }

        public synchronized void logError(String message, Throwable t) {
            if (writer != null) {
                writer.println("[ERROR] [" + LocalDateTime.now().format(FMT) + "] " + message);
                if (t != null) t.printStackTrace(writer);
                writer.flush();
            }
        }

        public synchronized void logRequest(String method, String uri, String query,
                                             Map<String, String> headers, int status, long durationMs, String ip) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=====================================\n");
            sb.append("TIMESTAMP   : ").append(LocalDateTime.now().format(FMT)).append("\n");
            sb.append("METHOD      : ").append(method).append("\n");
            sb.append("URI         : ").append(uri).append("\n");
            if (query != null) sb.append("QUERY       : ").append(query).append("\n");
            sb.append("CLIENT IP   : ").append(ip).append("\n");
            sb.append("STATUS      : ").append(status).append("\n");
            sb.append("DURATION    : ").append(durationMs).append(" ms\n");
            sb.append("HEADERS     : ").append(maskHeaders(headers)).append("\n");
            sb.append("=====================================");
            if (writer != null) {
                writer.println(sb.toString());
                writer.flush();
            }
        }

        private String maskHeaders(Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            headers.forEach((k, v) -> {
                if (k.equalsIgnoreCase("Authorization")) sb.append(k).append("=[MASKED], ");
                else sb.append(k).append("=").append(v).append(", ");
            });
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            sb.append("}");
            return sb.toString();
        }

        @PreDestroy
        public void cleanup() { if (writer != null) writer.close(); }
    }

    /**
     * HTTP request/response logging filter
     */
    @Component
    public static class LoggingFilter extends OncePerRequestFilter {

        private final AppLogger appLogger;

        public LoggingFilter(AppLogger appLogger) {
            this.appLogger = appLogger;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
            long start = System.currentTimeMillis();
            Map<String, String> headers = new LinkedHashMap<>();
            Enumeration<String> names = request.getHeaderNames();
            if (names != null) {
                while (names.hasMoreElements()) {
                    String n = names.nextElement();
                    headers.put(n, request.getHeader(n));
                }
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - start;
                appLogger.logRequest(request.getMethod(), request.getRequestURI(),
                        request.getQueryString(), headers, response.getStatus(), duration, request.getRemoteAddr());
            }
        }
    }

    @Configuration
    public static class LoggingFilterConfig {
        @Bean
        public FilterRegistrationBean<LoggingFilter> loggingFilterReg(LoggingFilter filter) {
            FilterRegistrationBean<LoggingFilter> reg = new FilterRegistrationBean<>();
            reg.setFilter(filter);
            reg.addUrlPatterns("/*");
            reg.setOrder(1);
            return reg;
        }
    }
}
