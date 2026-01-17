package com.izemtechnologies.logging.metrics;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.Getter;
import lombok.Setter;

/**
 * Logback appender that captures metrics for each log event.
 * 
 * <p>This appender works in conjunction with {@link LoggingMetrics} to record
 * statistics about log events without interfering with the actual logging.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Getter
@Setter
public class LoggingMetricsAppender extends AppenderBase<ILoggingEvent> {

    private static LoggingMetrics metrics;

    /**
     * Sets the metrics instance (called during auto-configuration).
     * 
     * @param metricsInstance the metrics to use
     */
    public static void setMetrics(LoggingMetrics metricsInstance) {
        metrics = metricsInstance;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (metrics == null) {
            return;
        }

        long startTime = System.nanoTime();

        try {
            String level = event.getLevel().toString();
            String loggerName = event.getLoggerName();

            // Calculate processing time (approximate)
            long durationNs = System.nanoTime() - startTime;

            metrics.recordEvent(level, loggerName, durationNs);

        } catch (Exception e) {
            // Never let metrics collection crash the application
            addWarn("Error recording logging metrics", e);
        }
    }
}
