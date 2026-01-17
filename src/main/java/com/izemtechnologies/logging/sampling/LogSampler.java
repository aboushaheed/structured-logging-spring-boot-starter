package com.izemtechnologies.logging.sampling;

import ch.qos.logback.classic.Level;
import com.izemtechnologies.logging.properties.LoggingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Intelligent log sampler that reduces log volume while preserving important information.
 * 
 * <p>This component implements multiple sampling strategies to help control log volume
 * in high-traffic production environments while ensuring critical logs are never lost.</p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Multiple sampling strategies (rate, adaptive, priority, etc.)</li>
 *   <li>Path-specific sampling rates</li>
 *   <li>Always sample errors (configurable)</li>
 *   <li>Adaptive rate adjustment based on load</li>
 *   <li>Metrics for monitoring sampling effectiveness</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogSampler {

    private final LoggingProperties properties;

    private final LongAdder totalEvents = new LongAdder();
    private final LongAdder sampledEvents = new LongAdder();
    private final LongAdder droppedEvents = new LongAdder();
    private final AtomicLong countBasedCounter = new AtomicLong(0);
    
    private final Map<String, LongAdder> eventsByPath = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> eventsByLogger = new ConcurrentHashMap<>();

    private volatile long lastLoadCheckTime = System.currentTimeMillis();
    private volatile double currentAdaptiveRate = 1.0;

    /**
     * Determines if a log event should be sampled (kept) or dropped.
     * 
     * @param level      the log level
     * @param loggerName the logger name
     * @param path       the request path (optional, for HTTP logs)
     * @return true if the event should be logged, false if it should be dropped
     */
    public boolean shouldSample(Level level, String loggerName, String path) {
        if (!isEnabled()) {
            return true;
        }

        totalEvents.increment();

        // Always sample errors if configured
        if (shouldAlwaysSampleLevel(level)) {
            sampledEvents.increment();
            return true;
        }

        boolean sampled = switch (getStrategy()) {
            case NONE -> true;
            case RATE -> sampleByRate(path);
            case ADAPTIVE -> sampleAdaptive(path);
            case PRIORITY -> sampleByPriority(level);
            case COUNT_BASED -> sampleByCount();
            case HEAD_BASED -> sampleHeadBased();
            case TAIL_BASED -> true; // Tail-based is handled differently
        };

        if (sampled) {
            sampledEvents.increment();
            trackEvent(path, loggerName);
        } else {
            droppedEvents.increment();
        }

        return sampled;
    }

    /**
     * Simplified sampling check for log events without path context.
     * 
     * @param level      the log level
     * @param loggerName the logger name
     * @return true if the event should be logged
     */
    public boolean shouldSample(Level level, String loggerName) {
        return shouldSample(level, loggerName, null);
    }

    /**
     * Marks the current request for tail-based sampling.
     * Call this when an error occurs to ensure all buffered logs are kept.
     */
    public void markForTailSampling() {
        TailSamplingContext.markForSampling();
    }

    /**
     * Checks if the current request is marked for tail-based sampling.
     * 
     * @return true if marked for sampling
     */
    public boolean isMarkedForTailSampling() {
        return TailSamplingContext.isMarkedForSampling();
    }

    // ==================== Sampling Strategies ====================

    private boolean sampleByRate(String path) {
        double rate = getEffectiveRate(path);
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private boolean sampleAdaptive(String path) {
        updateAdaptiveRate();
        double baseRate = getEffectiveRate(path);
        double effectiveRate = baseRate * currentAdaptiveRate;
        return ThreadLocalRandom.current().nextDouble() < effectiveRate;
    }

    private boolean sampleByPriority(Level level) {
        // ERROR and WARN always sampled (handled by shouldAlwaysSampleLevel)
        // INFO sampled at configured rate
        // DEBUG and TRACE sampled at reduced rate
        double rate = switch (level.toInt()) {
            case Level.INFO_INT -> properties.getSampling().getRate();
            case Level.DEBUG_INT -> properties.getSampling().getRate() * 0.5;
            case Level.TRACE_INT -> properties.getSampling().getRate() * 0.1;
            default -> properties.getSampling().getRate();
        };
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private boolean sampleByCount() {
        long count = countBasedCounter.incrementAndGet();
        int interval = properties.getSampling().getCountInterval();
        return count % interval == 0;
    }

    private boolean sampleHeadBased() {
        // Check if this request was marked for sampling at the start
        Boolean marked = HeadSamplingContext.isMarkedForSampling();
        if (marked == null) {
            // First log in this request - make the decision
            boolean sample = ThreadLocalRandom.current().nextDouble() < properties.getSampling().getRate();
            HeadSamplingContext.markSamplingDecision(sample);
            return sample;
        }
        return marked;
    }

    // ==================== Adaptive Rate Calculation ====================

    private void updateAdaptiveRate() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastLoadCheckTime;
        
        // Update every second
        if (elapsed < 1000) {
            return;
        }
        
        lastLoadCheckTime = now;
        
        // Calculate events per second
        long total = totalEvents.sum();
        double eventsPerSecond = total / (elapsed / 1000.0);
        
        // Adjust rate based on load
        int highLoadThreshold = properties.getSampling().getHighLoadThreshold();
        int criticalLoadThreshold = properties.getSampling().getCriticalLoadThreshold();
        
        if (eventsPerSecond > criticalLoadThreshold) {
            currentAdaptiveRate = 0.1; // 10% of base rate
        } else if (eventsPerSecond > highLoadThreshold) {
            currentAdaptiveRate = 0.5; // 50% of base rate
        } else {
            currentAdaptiveRate = 1.0; // Full rate
        }
        
        // Reset counters periodically
        if (elapsed > 60000) {
            totalEvents.reset();
            sampledEvents.reset();
            droppedEvents.reset();
        }
    }

    // ==================== Helper Methods ====================

    private double getEffectiveRate(String path) {
        if (path != null) {
            // Check path-specific rates
            Map<String, Double> pathRates = properties.getSampling().getPathRates();
            for (Map.Entry<String, Double> entry : pathRates.entrySet()) {
                if (matchesPath(path, entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return properties.getSampling().getRate();
    }

    private boolean matchesPath(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        }
        return path.equals(pattern);
    }

    private boolean shouldAlwaysSampleLevel(Level level) {
        if (!properties.getSampling().isAlwaysSampleErrors()) {
            return false;
        }
        return level.toInt() >= Level.WARN_INT;
    }

    private void trackEvent(String path, String loggerName) {
        if (path != null) {
            eventsByPath.computeIfAbsent(path, k -> new LongAdder()).increment();
        }
        if (loggerName != null) {
            eventsByLogger.computeIfAbsent(loggerName, k -> new LongAdder()).increment();
        }
    }

    private boolean isEnabled() {
        return properties.getSampling().isEnabled();
    }

    private SamplingStrategy getStrategy() {
        return properties.getSampling().getStrategy();
    }

    // ==================== Metrics ====================

    /**
     * Returns the total number of log events processed.
     */
    public long getTotalEvents() {
        return totalEvents.sum();
    }

    /**
     * Returns the number of sampled (kept) events.
     */
    public long getSampledEvents() {
        return sampledEvents.sum();
    }

    /**
     * Returns the number of dropped events.
     */
    public long getDroppedEvents() {
        return droppedEvents.sum();
    }

    /**
     * Returns the current sampling rate (for adaptive strategy).
     */
    public double getCurrentAdaptiveRate() {
        return currentAdaptiveRate;
    }

    /**
     * Returns the effective sampling percentage.
     */
    public double getSamplingPercentage() {
        long total = totalEvents.sum();
        if (total == 0) return 100.0;
        return (sampledEvents.sum() * 100.0) / total;
    }

    // ==================== Context Classes ====================

    /**
     * Thread-local context for head-based sampling decisions.
     */
    public static class HeadSamplingContext {
        private static final ThreadLocal<Boolean> SAMPLING_DECISION = new ThreadLocal<>();

        public static void markSamplingDecision(boolean sample) {
            SAMPLING_DECISION.set(sample);
        }

        public static Boolean isMarkedForSampling() {
            return SAMPLING_DECISION.get();
        }

        public static void clear() {
            SAMPLING_DECISION.remove();
        }
    }

    /**
     * Thread-local context for tail-based sampling.
     */
    public static class TailSamplingContext {
        private static final ThreadLocal<Boolean> MARKED_FOR_SAMPLING = new ThreadLocal<>();

        public static void markForSampling() {
            MARKED_FOR_SAMPLING.set(true);
        }

        public static boolean isMarkedForSampling() {
            return Boolean.TRUE.equals(MARKED_FOR_SAMPLING.get());
        }

        public static void clear() {
            MARKED_FOR_SAMPLING.remove();
        }
    }
}
