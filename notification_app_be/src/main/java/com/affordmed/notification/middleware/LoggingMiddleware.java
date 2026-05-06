package com.affordmed.notification.middleware;

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
import java.util.*;

public class LoggingMiddleware {

    @Service
    public static class AppLogger {
        private static final String LOG_FILE = "logs/app.log";
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private PrintWriter writer;

        public AppLogger() {
            try {
                new File("logs").mkdirs();
                this.writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)), true);
            } catch (IOException e) { throw new RuntimeException("Failed to init log file", e); }
        }

        public synchronized void log(String msg) {
            if (writer != null) { writer.println("[" + LocalDateTime.now().format(FMT) + "] " + msg); writer.flush(); }
        }

        public synchronized void logError(String msg, Throwable t) {
            if (writer != null) { writer.println("[ERROR][" + LocalDateTime.now().format(FMT) + "] " + msg); if (t != null) t.printStackTrace(writer); writer.flush(); }
        }

        public synchronized void logRequest(String method, String uri, String query, Map<String,String> headers, int status, long ms, String ip) {
            StringBuilder sb = new StringBuilder("\n=====================================\n");
            sb.append("TIME: ").append(LocalDateTime.now().format(FMT)).append(" | ").append(method).append(" ").append(uri);
            if (query != null) sb.append("?").append(query);
            sb.append(" | STATUS: ").append(status).append(" | ").append(ms).append("ms | IP: ").append(ip);
            sb.append("\n=====================================");
            if (writer != null) { writer.println(sb); writer.flush(); }
        }

        @PreDestroy
        public void cleanup() { if (writer != null) writer.close(); }
    }

    @Component
    public static class LoggingFilter extends OncePerRequestFilter {
        private final AppLogger logger;
        public LoggingFilter(AppLogger logger) { this.logger = logger; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            long start = System.currentTimeMillis();
            Map<String,String> hdrs = new LinkedHashMap<>();
            Enumeration<String> names = req.getHeaderNames();
            if (names != null) while (names.hasMoreElements()) { String n = names.nextElement(); hdrs.put(n, req.getHeader(n)); }
            try { chain.doFilter(req, res); }
            finally { logger.logRequest(req.getMethod(), req.getRequestURI(), req.getQueryString(), hdrs, res.getStatus(), System.currentTimeMillis()-start, req.getRemoteAddr()); }
        }
    }

    @Configuration
    public static class FilterConfig {
        @Bean
        public FilterRegistrationBean<LoggingFilter> loggingReg(LoggingFilter f) {
            FilterRegistrationBean<LoggingFilter> r = new FilterRegistrationBean<>();
            r.setFilter(f); r.addUrlPatterns("/*"); r.setOrder(1); return r;
        }
    }
}
