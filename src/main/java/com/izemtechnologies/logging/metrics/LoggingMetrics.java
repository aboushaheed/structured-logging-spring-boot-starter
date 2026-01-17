package com.izemtechnologies.logging.metrics;

import com.izemtechnologies.logging.async.BackpressureAsyncAppender;
import com.izemtechnologies.logging.sampling.LogSampler;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Micrometer metrics for the logging system.
 * 
 * <p>This component exposes various metrics about the logging system's health
 * and performance, enabling monitoring and alerting on log-related issues.</p>
 * 
 * <h2>Exposed Metrics:</h2>
 * <table>
 *   <tr><th>Metric</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>logging.events.total</td><td>Counter</td><td>Total log events processed</td></tr>
 *   <tr><td>logging.events.by_level</td><td>Counter</td><td>Events by log level</td></tr>
 *   <tr><td>logging.events.dropped</td><td>Counter</td><td>Events dropped due to sampling/backpressure</td></tr>
 *   <tr><td>logging.buffer.utilization</td><td>Gauge</td><td>Async buffer utilization percentage</td></tr>
 *   <tr><td>logging.latency</td><td>Timer</td><td>Time to process log events</td></tr>
 *   <tr><td>logging.sampling.rate</td><td>Gauge</td><td>Current effective sampling rate</td></tr>
 * </table>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingMetrics implements MeterBinder {

    private static final String METRIC_PREFIX = "logging";

    private final MeterRegistry meterRegistry;

    private final LongAdder totalEvents = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> eventsByLevel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> eventsByLogger = new ConcurrentHashMap<>();
    private final LongAdder droppedEvents = new LongAdder();
    private final LongAdder maskedEvents = new LongAdder();
    private final LongAdder errorEvents = new LongAdder();

    private Timer loggingLatency;
    private Counter totalEventsCounter;
    private Counter droppedEventsCounter;
    private Counter maskedEventsCounter;

    private LogSampler sampler;
    private BackpressureAsyncAppender asyncAppender;

    @Override
    public void bindTo(MeterRegistry registry) {
        // Total events counter
        totalEventsCounter = Counter.builder(METRIC_PREFIX + ".events.total")
            .description("Total number of log events processed")
            .register(registry);

        // Dropped events counter
        droppedEventsCounter = Counter.builder(METRIC_PREFIX + ".events.dropped")
            .description("Number of log events dropped due to sampling or backpressure")
            .register(registry);

        // Masked events counter
        maskedEventsCounter = Counter.builder(METRIC_PREFIX + ".events.masked")
            .description("Number of log events with masked sensitive data")
            .register(registry);

        // Events by level
        for (String level : new String[]{"ERROR", "WARN", "INFO", "DEBUG", "TRACE"}) {
            Counter.builder(METRIC_PREFIX + ".events.by_level")
                .tag("level", level)
                .description("Log events by level")
                .register(registry);
        }

        // Logging latency timer
        loggingLatency = Timer.builder(METRIC_PREFIX + ".latency")
            .description("Time taken to process log events")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        // Buffer utilization gauge
        Gauge.builder(METRIC_PREFIX + ".buffer.utilization", this, LoggingMetrics::getBufferUtilization)
            .description("Async buffer utilization percentage")
            .register(registry);

        // Buffer size gauge
        Gauge.builder(METRIC_PREFIX + ".buffer.size", this, LoggingMetrics::getBufferSize)
            .description("Current number of events in async buffer")
            .register(registry);

        // Sampling rate gauge
        Gauge.builder(METRIC_PREFIX + ".sampling.rate", this, LoggingMetrics::getSamplingRate)
            .description("Current effective sampling rate")
            .register(registry);

        // Sampling percentage gauge
        Gauge.builder(METRIC_PREFIX + ".sampling.percentage", this, LoggingMetrics::getSamplingPercentage)
            .description("Percentage of events being sampled")
            .register(registry);

        // Error rate gauge
        Gauge.builder(METRIC_PREFIX + ".error.rate", this, LoggingMetrics::getErrorRate)
            .description("Percentage of ERROR level events")
            .register(registry);
    }

    @PostConstruct
    public void init() {
        bindTo(meterRegistry);
        log.info("Logging metrics initialized");
    }

    // ==================== Recording Methods ====================

    /**
     * Records a log event.
     * 
     * @param level      the log level
     * @param loggerName the logger name
     * @param durationNs the processing duration in nanoseconds
     */
    public void recordEvent(String level, String loggerName, long durationNs) {
        totalEvents.increment();
        totalEventsCounter.increment();

        // By level
        eventsByLevel.computeIfAbsent(level, k -> new LongAdder()).increment();
        meterRegistry.counter(METRIC_PREFIX + ".events.by_level", "level", level).increment();

        // By logger (top 100 only to prevent cardinality explosion)
        if (eventsByLogger.size() < 100) {
            eventsByLogger.computeIfAbsent(loggerName, k -> new LongAdder()).increment();
        }

        // Track errors
        if ("ERROR".equals(level)) {
            errorEvents.increment();
        }

        // Latency
        if (durationNs > 0) {
            loggingLatency.record(durationNs, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Records a dropped event.
     * 
     * @param reason the reason for dropping (sampling, backpressure, etc.)
     */
    public void recordDroppedEvent(String reason) {
        droppedEvents.increment();
        droppedEventsCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".events.dropped.by_reason", "reason", reason).increment();
    }

    /**
     * Records a masked event.
     * 
     * @param maskType the type of masking applied
     */
    public void recordMaskedEvent(String maskType) {
        maskedEvents.increment();
        maskedEventsCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".events.masked.by_type", "type", maskType).increment();
    }

    /**
     * Records logging latency.
     * 
     * @param durationNs duration in nanoseconds
     */
    public void recordLatency(long durationNs) {
        loggingLatency.record(durationNs, TimeUnit.NANOSECONDS);
    }

    // ==================== Gauge Value Methods ====================

    private double getBufferUtilization() {
        if (asyncAppender != null) {
            return asyncAppender.getBufferUtilization();
        }
        return 0.0;
    }

    private double getBufferSize() {
        if (asyncAppender != null) {
            return asyncAppender.getNumberOfElementsInQueue();
        }
        return 0.0;
    }

    private double getSamplingRate() {
        if (sampler != null) {
            return sampler.getCurrentAdaptiveRate();
        }
        return 1.0;
    }

    private double getSamplingPercentage() {
        if (sampler != null) {
            return sampler.getSamplingPercentage();
        }
        return 100.0;
    }

    private double getErrorRate() {
        long total = totalEvents.sum();
        if (total == 0) return 0.0;
        return (errorEvents.sum() * 100.0) / total;
    }

    // ==================== Setters for Dependencies ====================

    public void setSampler(LogSampler sampler) {
        this.sampler = sampler;
    }

    public void setAsyncAppender(BackpressureAsyncAppender asyncAppender) {
        this.asyncAppender = asyncAppender;
    }

    // ==================== Statistics ====================

    /**
     * Returns current statistics as a map.
     */
    public java.util.Map<String, Object> getStatistics() {
        return java.util.Map.of(
            "totalEvents", totalEvents.sum(),
            "droppedEvents", droppedEvents.sum(),
            "maskedEvents", maskedEvents.sum(),
            "errorEvents", errorEvents.sum(),
            "bufferUtilization", getBufferUtilization(),
            "samplingRate", getSamplingRate(),
            "errorRate", getErrorRate()
        );
    }
}
