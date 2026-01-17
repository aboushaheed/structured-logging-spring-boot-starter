package com.izemtechnologies.logging.context;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-local context for request-scoped log tags.
 * 
 * <p>This class provides isolation between concurrent requests by storing tags
 * in a {@link ThreadLocal}. Each thread (request) has its own set of tags that
 * are automatically cleaned up when the request completes.</p>
 * 
 * <p>Usage with the {@link com.izemtechnologies.logging.filter.RequestLoggingFilter}
 * ensures automatic initialization and cleanup of the context.</p>
 * 
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // In a request handler
 * RequestScopedLogContext.set("user_id", userId);
 * RequestScopedLogContext.set("tenant_id", tenantId);
 * 
 * // Tags are automatically included in all logs within this request
 * log.info("Processing request"); // Includes user_id and tenant_id
 * 
 * // Context is automatically cleared by the filter
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
public final class RequestScopedLogContext {

    private static final ThreadLocal<Map<String, Object>> REQUEST_TAGS = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Tracks whether the context has been initialized for the current thread.
     * This helps detect misuse (e.g., forgetting to call clear()).
     */
    private static final ThreadLocal<Boolean> INITIALIZED = 
        ThreadLocal.withInitial(() -> false);

    private RequestScopedLogContext() {
        // Utility class - prevent instantiation
    }

    /**
     * Initializes the context for the current request/thread.
     * Called automatically by {@link com.izemtechnologies.logging.filter.RequestLoggingFilter}.
     */
    public static void initialize() {
        REQUEST_TAGS.set(new ConcurrentHashMap<>());
        INITIALIZED.set(true);
    }

    /**
     * Sets a tag in the current request context.
     * 
     * @param key   the tag key (must not be null or blank)
     * @param value the tag value (must not be null)
     * @throws IllegalArgumentException if key is null or blank
     * @throws NullPointerException if value is null
     */
    public static void set(String key, Object value) {
        validateKey(key);
        if (value == null) {
            throw new NullPointerException("Tag value cannot be null for key: " + key);
        }
        REQUEST_TAGS.get().put(key, value);
    }

    /**
     * Alias for {@link #set(String, Object)} for convenience.
     * 
     * @param key   the tag key (must not be null or blank)
     * @param value the tag value (must not be null)
     */
    public static void put(String key, Object value) {
        set(key, value);
    }

    /**
     * Sets multiple tags at once in the current request context.
     * 
     * @param tags map of tags to set
     */
    public static void setAll(Map<String, Object> tags) {
        if (tags != null) {
            tags.forEach(RequestScopedLogContext::set);
        }
    }

    /**
     * Gets a tag value from the current request context.
     * 
     * @param key the tag key
     * @return the tag value, or null if not present
     */
    public static Object get(String key) {
        if (key == null) {
            return null;
        }
        return REQUEST_TAGS.get().get(key);
    }

    /**
     * Gets a tag value with type casting.
     * 
     * @param key  the tag key
     * @param type the expected type
     * @param <T>  the type parameter
     * @return the tag value cast to the specified type, or null if not present
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets a tag value as a String.
     * 
     * @param key the tag key
     * @return the tag value as String, or null if not present
     */
    public static String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Removes a tag from the current request context.
     * 
     * @param key the tag key to remove
     * @return the previous value, or null if not present
     */
    public static Object remove(String key) {
        if (key == null) {
            return null;
        }
        return REQUEST_TAGS.get().remove(key);
    }

    /**
     * Checks if a tag exists in the current request context.
     * 
     * @param key the tag key
     * @return true if the tag exists
     */
    public static boolean has(String key) {
        if (key == null) {
            return false;
        }
        return REQUEST_TAGS.get().containsKey(key);
    }

    /**
     * Returns an unmodifiable view of all tags in the current request context.
     * 
     * @return unmodifiable map of all tags
     */
    public static Map<String, Object> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(REQUEST_TAGS.get()));
    }

    /**
     * Returns a direct reference to the underlying map for high-performance access.
     * <p><strong>Warning:</strong> This bypasses immutability guarantees. Use with caution.</p>
     * 
     * @return direct reference to the tags map
     */
    public static Map<String, Object> getReference() {
        return REQUEST_TAGS.get();
    }

    /**
     * Clears all tags from the current request context.
     * <p><strong>Important:</strong> This must be called at the end of each request
     * to prevent memory leaks. The {@link com.izemtechnologies.logging.filter.RequestLoggingFilter}
     * handles this automatically.</p>
     */
    public static void clear() {
        REQUEST_TAGS.remove();
        INITIALIZED.remove();
    }

    /**
     * Checks if the context is empty.
     * 
     * @return true if no tags are set
     */
    public static boolean isEmpty() {
        return REQUEST_TAGS.get().isEmpty();
    }

    /**
     * Returns the number of tags in the current context.
     * 
     * @return the tag count
     */
    public static int size() {
        return REQUEST_TAGS.get().size();
    }

    /**
     * Checks if the context has been initialized for the current thread.
     * 
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    /**
     * Executes a runnable with temporary tags that are automatically removed afterward.
     * 
     * @param temporaryTags tags to add temporarily
     * @param runnable      the code to execute
     */
    public static void withTags(Map<String, Object> temporaryTags, Runnable runnable) {
        Map<String, Object> previousValues = new HashMap<>();
        
        try {
            // Store previous values and set new ones
            for (Map.Entry<String, Object> entry : temporaryTags.entrySet()) {
                Object previous = REQUEST_TAGS.get().put(entry.getKey(), entry.getValue());
                if (previous != null) {
                    previousValues.put(entry.getKey(), previous);
                }
            }
            
            runnable.run();
            
        } finally {
            // Restore previous values or remove temporary tags
            for (String key : temporaryTags.keySet()) {
                Object previous = previousValues.get(key);
                if (previous != null) {
                    REQUEST_TAGS.get().put(key, previous);
                } else {
                    REQUEST_TAGS.get().remove(key);
                }
            }
        }
    }

    /**
     * Executes a supplier with temporary tags that are automatically removed afterward.
     * 
     * @param temporaryTags tags to add temporarily
     * @param supplier      the code to execute
     * @param <T>           the return type
     * @return the result of the supplier
     */
    public static <T> T withTags(Map<String, Object> temporaryTags, Supplier<T> supplier) {
        Map<String, Object> previousValues = new HashMap<>();
        
        try {
            // Store previous values and set new ones
            for (Map.Entry<String, Object> entry : temporaryTags.entrySet()) {
                Object previous = REQUEST_TAGS.get().put(entry.getKey(), entry.getValue());
                if (previous != null) {
                    previousValues.put(entry.getKey(), previous);
                }
            }
            
            return supplier.get();
            
        } finally {
            // Restore previous values or remove temporary tags
            for (String key : temporaryTags.keySet()) {
                Object previous = previousValues.get(key);
                if (previous != null) {
                    REQUEST_TAGS.get().put(key, previous);
                } else {
                    REQUEST_TAGS.get().remove(key);
                }
            }
        }
    }

    /**
     * Creates a snapshot of the current context that can be transferred to another thread.
     * Useful for async operations where you want to preserve the logging context.
     * 
     * @return a snapshot of the current tags
     */
    public static Map<String, Object> snapshot() {
        return new HashMap<>(REQUEST_TAGS.get());
    }

    /**
     * Restores a previously captured snapshot to the current thread.
     * 
     * @param snapshot the snapshot to restore
     */
    public static void restore(Map<String, Object> snapshot) {
        if (snapshot != null) {
            REQUEST_TAGS.get().putAll(snapshot);
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Tag key cannot be null or blank");
        }
    }
}
