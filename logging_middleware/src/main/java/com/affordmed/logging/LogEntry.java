package com.affordmed.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Represents a single log entry captured by the logging middleware.
 */
public class LogEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final String httpMethod;
    private final String requestUri;
    private final String queryString;
    private final Map<String, String> requestHeaders;
    private final int responseStatus;
    private final long durationMs;
    private final String clientIp;

    public LogEntry(String httpMethod, String requestUri, String queryString,
                    Map<String, String> requestHeaders, int responseStatus,
                    long durationMs, String clientIp) {
        this.timestamp = LocalDateTime.now();
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.queryString = queryString;
        this.requestHeaders = requestHeaders;
        this.responseStatus = responseStatus;
        this.durationMs = durationMs;
        this.clientIp = clientIp;
    }

    /**
     * Formats the log entry as a structured string for file output.
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=====================================\n");
        sb.append("TIMESTAMP   : ").append(timestamp.format(FORMATTER)).append("\n");
        sb.append("METHOD      : ").append(httpMethod).append("\n");
        sb.append("URI         : ").append(requestUri).append("\n");
        if (queryString != null && !queryString.isEmpty()) {
            sb.append("QUERY       : ").append(queryString).append("\n");
        }
        sb.append("CLIENT IP   : ").append(clientIp).append("\n");
        sb.append("STATUS      : ").append(responseStatus).append("\n");
        sb.append("DURATION    : ").append(durationMs).append(" ms\n");
        sb.append("HEADERS     : ").append(formatHeaders()).append("\n");
        sb.append("=====================================\n");
        return sb.toString();
    }

    private String formatHeaders() {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        requestHeaders.forEach((key, value) -> {
            // Mask authorization tokens for security
            if (key.equalsIgnoreCase("Authorization")) {
                sb.append(key).append("=").append("[MASKED]").append(", ");
            } else {
                sb.append(key).append("=").append(value).append(", ");
            }
        });
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }

    // Getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestUri() { return requestUri; }
    public int getResponseStatus() { return responseStatus; }
    public long getDurationMs() { return durationMs; }
}
