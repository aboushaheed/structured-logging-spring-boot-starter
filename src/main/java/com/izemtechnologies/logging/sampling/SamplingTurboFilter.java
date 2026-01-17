package com.izemtechnologies.logging.sampling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;

/**
 * Logback TurboFilter that applies intelligent sampling to log events.
 * 
 * <p>TurboFilters are evaluated before the logging event is created, making them
 * more efficient than regular filters for high-volume sampling scenarios.</p>
 * 
 * <p>This filter integrates with {@link LogSampler} to apply the configured
 * sampling strategy to all log events.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class SamplingTurboFilter extends TurboFilter {

    private static LogSampler sampler;

    /**
     * Marker name that forces sampling (log is always kept).
     */
    public static final String FORCE_SAMPLE_MARKER = "FORCE_SAMPLE";

    /**
     * Marker name that forces dropping (log is always dropped).
     */
    public static final String FORCE_DROP_MARKER = "FORCE_DROP";

    /**
     * Sets the sampler instance (called during auto-configuration).
     * 
     * @param samplerInstance the sampler to use
     */
    public static void setSampler(LogSampler samplerInstance) {
        sampler = samplerInstance;
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        
        // If sampler not initialized, allow all
        if (sampler == null) {
            return FilterReply.NEUTRAL;
        }

        // Check for force markers
        if (marker != null) {
            if (FORCE_SAMPLE_MARKER.equals(marker.getName())) {
                return FilterReply.ACCEPT;
            }
            if (FORCE_DROP_MARKER.equals(marker.getName())) {
                return FilterReply.DENY;
            }
        }

        // Apply sampling decision
        String loggerName = logger.getName();
        String path = extractPathFromContext();

        if (sampler.shouldSample(level, loggerName, path)) {
            return FilterReply.NEUTRAL; // Allow further processing
        } else {
            return FilterReply.DENY; // Drop the log event
        }
    }

    /**
     * Extracts the request path from the current context (if available).
     * This is used for path-specific sampling rates.
     */
    private String extractPathFromContext() {
        try {
            // Try to get from MDC
            String path = org.slf4j.MDC.get("uri");
            if (path != null) {
                return path;
            }
            
            // Try to get from RequestScopedLogContext
            Object pathObj = com.izemtechnologies.logging.context.RequestScopedLogContext.get("uri");
            if (pathObj instanceof String) {
                return (String) pathObj;
            }
        } catch (Exception e) {
            // Ignore - context might not be available
        }
        return null;
    }
}
