package com.izemtechnologies.logging.health;

import com.izemtechnologies.logging.sampling.LogSampler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Optional;

/**
 * Health indicator for the logging subsystem.
 *
 * <p>Exposes health information about the logging infrastructure including:</p>
 * <ul>
 *   <li>Sampling statistics (if sampling is enabled)</li>
 *   <li>Overall logging subsystem status</li>
 * </ul>
 *
 * <p>Health status is determined by:</p>
 * <ul>
 *   <li>UP: Everything is working normally</li>
 *   <li>DEGRADED: Sampling rate has dropped significantly (adaptive sampling under load)</li>
 *   <li>DOWN: Critical issues detected</li>
 * </ul>
 *
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class LoggingHealthIndicator implements HealthIndicator {

    private final Optional<LogSampler> logSampler;

    /**
     * Threshold for adaptive rate below which health is considered degraded.
     */
    private static final double DEGRADED_ADAPTIVE_RATE_THRESHOLD = 0.3;

    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();

            // Add basic status
            builder.withDetail("status", "operational");

            // Add sampling statistics if available
            logSampler.ifPresent(sampler -> {
                builder.withDetail("sampling", buildSamplingDetails(sampler));
            });

            // Check for degraded conditions
            if (logSampler.isPresent()) {
                LogSampler sampler = logSampler.get();
                double adaptiveRate = sampler.getCurrentAdaptiveRate();

                if (adaptiveRate < DEGRADED_ADAPTIVE_RATE_THRESHOLD) {
                    return Health.status("DEGRADED")
                        .withDetail("reason", "High load - adaptive sampling reduced to " +
                            String.format("%.1f%%", adaptiveRate * 100))
                        .withDetail("sampling", buildSamplingDetails(sampler))
                        .build();
                }
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Error checking logging health", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    /**
     * Builds sampling details map for health response.
     */
    private java.util.Map<String, Object> buildSamplingDetails(LogSampler sampler) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("enabled", true);
        details.put("totalEvents", sampler.getTotalEvents());
        details.put("sampledEvents", sampler.getSampledEvents());
        details.put("droppedEvents", sampler.getDroppedEvents());
        details.put("samplingPercentage", String.format("%.2f%%", sampler.getSamplingPercentage()));
        details.put("currentAdaptiveRate", String.format("%.2f", sampler.getCurrentAdaptiveRate()));
        return details;
    }
}
