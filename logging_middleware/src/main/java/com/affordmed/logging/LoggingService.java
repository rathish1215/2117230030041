package com.affordmed.logging;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Custom file-based logging service.
 * Writes log entries to a file — NO built-in loggers (SLF4J, Log4j, System.out) used.
 * This is the MANDATORY logging mechanism for the Affordmed evaluation.
 */
@Service
public class LoggingService {

    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logs/app.log";

    private PrintWriter writer;

    public LoggingService() {
        initializeLogFile();
    }

    /**
     * Initializes the log directory and file.
     */
    private void initializeLogFile() {
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            // Append mode = true so logs persist across restarts
            FileWriter fw = new FileWriter(LOG_FILE, true);
            BufferedWriter bw = new BufferedWriter(fw);
            this.writer = new PrintWriter(bw, true); // autoFlush = true
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize log file: " + LOG_FILE, e);
        }
    }

    /**
     * Writes a LogEntry to the log file.
     */
    public synchronized void log(LogEntry entry) {
        if (writer != null) {
            writer.println(entry.toLogString());
            writer.flush();
        }
    }

    /**
     * Writes a custom message to the log file.
     */
    public synchronized void log(String message) {
        if (writer != null) {
            writer.println("[" + java.time.LocalDateTime.now() + "] " + message);
            writer.flush();
        }
    }

    /**
     * Writes an error with exception details to the log file.
     */
    public synchronized void logError(String message, Throwable throwable) {
        if (writer != null) {
            writer.println("[ERROR] [" + java.time.LocalDateTime.now() + "] " + message);
            if (throwable != null) {
                throwable.printStackTrace(writer);
            }
            writer.flush();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (writer != null) {
            writer.close();
        }
    }
}
