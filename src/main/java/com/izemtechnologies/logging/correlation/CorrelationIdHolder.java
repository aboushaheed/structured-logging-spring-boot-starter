package com.izemtechnologies.logging.correlation;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thread-local holder for correlation IDs used in distributed tracing.
 * 
 * <p>The correlation ID is a unique identifier that follows a request through
 * all services in a distributed system, enabling end-to-end tracing and debugging.</p>
 * 
 * <p>This class automatically synchronizes with SLF4J's MDC (Mapped Diagnostic Context)
 * to ensure the correlation ID appears in all log messages.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Set correlation ID (usually done by CorrelationIdFilter)
 * CorrelationIdHolder.set("abc-123-def");
 * 
 * // Get current correlation ID
 * String correlationId = CorrelationIdHolder.get();
 * 
 * // Generate new if not present
 * String correlationId = CorrelationIdHolder.getOrGenerate();
 * 
 * // Execute with specific correlation ID
 * CorrelationIdHolder.withCorrelationId("custom-id", () -> {
 *     // All logs here will have this correlation ID
 *     log.info("Processing...");
 * });
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
public final class CorrelationIdHolder {

    /**
     * Default MDC key for correlation ID.
     */
    public static final String MDC_KEY = "correlationId";

    /**
     * Alternative MDC key (X-Correlation-ID format).
     */
    public static final String MDC_KEY_ALT = "X-Correlation-ID";

    /**
     * HTTP header name for correlation ID.
     */
    public static final String HEADER_NAME = "X-Correlation-ID";

    /**
     * Alternative HTTP header names.
     */
    public static final String[] ALTERNATIVE_HEADERS = {
        "X-Request-ID",
        "X-Trace-ID",
        "Request-ID",
        "Correlation-ID"
    };

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    /**
     * Custom ID generator (can be overridden).
     */
    private static Supplier<String> idGenerator = CorrelationIdHolder::defaultIdGenerator;

    private CorrelationIdHolder() {
        // Utility class
    }

    /**
     * Sets the correlation ID for the current thread.
     * Also updates the MDC for logging.
     * 
     * @param correlationId the correlation ID to set
     */
    public static void set(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            CORRELATION_ID.set(correlationId);
            MDC.put(MDC_KEY, correlationId);
            MDC.put(MDC_KEY_ALT, correlationId);
        }
    }

    /**
     * Gets the correlation ID for the current thread.
     * 
     * @return the correlation ID, or null if not set
     */
    public static String get() {
        String id = CORRELATION_ID.get();
        if (id == null) {
            // Try to get from MDC (might have been set elsewhere)
            id = MDC.get(MDC_KEY);
        }
        return id;
    }

    /**
     * Gets the correlation ID, generating a new one if not present.
     * 
     * @return the existing or newly generated correlation ID
     */
    public static String getOrGenerate() {
        String id = get();
        if (id == null || id.isBlank()) {
            id = generate();
            set(id);
        }
        return id;
    }

    /**
     * Generates a new correlation ID without setting it.
     * 
     * @return a new correlation ID
     */
    public static String generate() {
        return idGenerator.get();
    }

    /**
     * Clears the correlation ID for the current thread.
     * Also removes from MDC.
     */
    public static void clear() {
        CORRELATION_ID.remove();
        MDC.remove(MDC_KEY);
        MDC.remove(MDC_KEY_ALT);
    }

    /**
     * Checks if a correlation ID is set for the current thread.
     * 
     * @return true if a correlation ID is present
     */
    public static boolean isSet() {
        return get() != null;
    }

    /**
     * Executes a runnable with a specific correlation ID.
     * The previous correlation ID is restored after execution.
     * 
     * @param correlationId the correlation ID to use
     * @param runnable      the code to execute
     */
    public static void withCorrelationId(String correlationId, Runnable runnable) {
        String previous = get();
        try {
            set(correlationId);
            runnable.run();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }

    /**
     * Executes a supplier with a specific correlation ID.
     * The previous correlation ID is restored after execution.
     * 
     * @param correlationId the correlation ID to use
     * @param supplier      the code to execute
     * @param <T>           the return type
     * @return the result of the supplier
     */
    public static <T> T withCorrelationId(String correlationId, Supplier<T> supplier) {
        String previous = get();
        try {
            set(correlationId);
            return supplier.get();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }

    /**
     * Creates a snapshot of the current correlation context for transfer to another thread.
     * 
     * @return the current correlation ID (can be null)
     */
    public static String snapshot() {
        return get();
    }

    /**
     * Restores a correlation context snapshot to the current thread.
     * 
     * @param correlationId the correlation ID to restore
     */
    public static void restore(String correlationId) {
        if (correlationId != null) {
            set(correlationId);
        }
    }

    /**
     * Sets a custom ID generator for correlation IDs.
     * 
     * @param generator the generator function
     */
    public static void setIdGenerator(Supplier<String> generator) {
        if (generator != null) {
            idGenerator = generator;
        }
    }

    /**
     * Resets to the default ID generator.
     */
    public static void resetIdGenerator() {
        idGenerator = CorrelationIdHolder::defaultIdGenerator;
    }

    /**
     * Default ID generator: timestamp prefix + UUID suffix for sortability and uniqueness.
     * Format: {timestamp}-{uuid-prefix}
     * Example: 1705312800000-a1b2c3d4
     */
    private static String defaultIdGenerator() {
        long timestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return timestamp + "-" + uuid;
    }
}
