package com.izemtechnologies.logging.enrichment;

import java.util.Map;

/**
 * Interface for log enrichment providers.
 * 
 * <p>Implementations of this interface can add additional context to log events
 * automatically. Multiple enrichers can be registered and will be called in order.</p>
 * 
 * <h2>Example Implementation:</h2>
 * <pre>{@code
 * @Component
 * public class UserContextEnricher implements LogEnricher {
 *     
 *     @Override
 *     public Map<String, Object> enrich() {
 *         User user = SecurityContextHolder.getContext().getAuthentication();
 *         if (user != null) {
 *             return Map.of(
 *                 "user_id", user.getId(),
 *                 "user_role", user.getRole()
 *             );
 *         }
 *         return Map.of();
 *     }
 *     
 *     @Override
 *     public int getOrder() {
 *         return 100; // Run after system enrichers
 *     }
 * }
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
public interface LogEnricher {

    /**
     * Returns additional fields to add to log events.
     * 
     * <p>This method is called for every log event, so implementations should
     * be efficient and avoid expensive operations.</p>
     * 
     * @return map of field names to values (never null)
     */
    Map<String, Object> enrich();

    /**
     * Returns the order in which this enricher should be applied.
     * Lower values are applied first.
     * 
     * @return the order value (default: 0)
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Returns whether this enricher is currently active.
     * Can be used to conditionally disable enrichment.
     * 
     * @return true if active
     */
    default boolean isActive() {
        return true;
    }

    /**
     * Returns the name of this enricher for logging/debugging.
     * 
     * @return the enricher name
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
