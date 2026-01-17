package com.izemtechnologies.logging.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * User-facing API for managing dynamic log metadata at runtime.
 * <p>
 * This class provides a clean, fluent API for adding, removing, and managing
 * dynamic tags that will be automatically included in every JSON log line.
 * It wraps the underlying {@link RuntimeLogTagsBean} and provides additional
 * convenience methods and validation.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Service
 * public class MyService {
 *
 *     private final LogMetadataManager logMetadataManager;
 *
 *     public MyService(LogMetadataManager logMetadataManager) {
 *         this.logMetadataManager = logMetadataManager;
 *     }
 *
 *     public void processRequest(String tenantId, String clusterId) {
 *         // Add dynamic tags that will appear in all subsequent logs
 *         logMetadataManager
 *             .addTag("tenant_id", tenantId)
 *             .addTag("cluster_id", clusterId);
 *
 *         // These tags will now appear in every log line
 *         logger.info("Processing request");
 *     }
 * }
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe. All operations are delegated to the underlying
 * {@link RuntimeLogTagsBean} which uses {@link java.util.concurrent.ConcurrentHashMap}.
 * </p>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@ToString(exclude = "runtimeTags")
public class LogMetadataManager {

    /**
     * The underlying tags storage bean.
     */
    @Getter
    private final RuntimeLogTagsBean runtimeTags;

    /**
     * Adds or updates a tag with the specified key and value.
     * <p>
     * The tag will be included in all subsequent JSON log lines until removed.
     * </p>
     *
     * @param key   the tag key (must not be null or empty)
     * @param value the tag value (must not be null)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if key is null/empty or value is null
     */
    public LogMetadataManager addTag(String key, Object value) {
        runtimeTags.addTag(key, value);
        log.debug("Added dynamic log tag: {}={}", key, value);
        return this;
    }

    /**
     * Adds or updates a tag with a lazily computed value.
     * <p>
     * The supplier is invoked immediately and the result is stored.
     * This is useful when the value computation might be expensive.
     * </p>
     *
     * @param key           the tag key (must not be null or empty)
     * @param valueSupplier supplier that provides the tag value
     * @return this instance for method chaining
     */
    public LogMetadataManager addTag(String key, Supplier<Object> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "Value supplier must not be null");
        return addTag(key, valueSupplier.get());
    }

    /**
     * Adds multiple tags at once from the provided map.
     *
     * @param tags map of tags to add (must not be null)
     * @return this instance for method chaining
     */
    public LogMetadataManager addTags(Map<String, Object> tags) {
        runtimeTags.addTags(tags);
        log.debug("Added {} dynamic log tags", tags.size());
        return this;
    }

    /**
     * Removes a tag with the specified key.
     *
     * @param key the tag key to remove
     * @return this instance for method chaining
     */
    public LogMetadataManager removeTag(String key) {
        Object removed = runtimeTags.removeTag(key);
        if (removed != null) {
            log.debug("Removed dynamic log tag: {}", key);
        }
        return this;
    }

    /**
     * Removes multiple tags at once.
     *
     * @param keys the tag keys to remove
     * @return this instance for method chaining
     */
    public LogMetadataManager removeTags(String... keys) {
        if (keys != null) {
            for (String key : keys) {
                removeTag(key);
            }
        }
        return this;
    }

    /**
     * Retrieves the value of a tag by its key.
     *
     * @param key the tag key
     * @return the tag value, or null if not found
     */
    public Object getTag(String key) {
        return runtimeTags.getTag(key);
    }

    /**
     * Retrieves the value of a tag, cast to the expected type.
     *
     * @param key  the tag key
     * @param type the expected type class
     * @param <T>  the expected type
     * @return the tag value cast to the expected type, or null if not found
     * @throws ClassCastException if the value cannot be cast to the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getTag(String key, Class<T> type) {
        Object value = runtimeTags.getTag(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Checks if a tag with the specified key exists.
     *
     * @param key the tag key to check
     * @return true if the tag exists, false otherwise
     */
    public boolean hasTag(String key) {
        return runtimeTags.hasTag(key);
    }

    /**
     * Returns an unmodifiable view of all current tags.
     *
     * @return unmodifiable map of all tags
     */
    public Map<String, Object> getAllTags() {
        return runtimeTags.getAllTags();
    }

    /**
     * Removes all dynamic tags.
     *
     * @return this instance for method chaining
     */
    public LogMetadataManager clearAllTags() {
        runtimeTags.clearAllTags();
        log.debug("Cleared all dynamic log tags");
        return this;
    }

    /**
     * Returns the current number of tags.
     *
     * @return the number of tags
     */
    public int getTagCount() {
        return runtimeTags.size();
    }

    /**
     * Checks if there are no tags.
     *
     * @return true if no tags exist, false otherwise
     */
    public boolean isEmpty() {
        return runtimeTags.isEmpty();
    }

    /**
     * Executes a block of code with temporary tags that are automatically removed afterward.
     * <p>
     * This is useful for request-scoped tags that should only exist during a specific operation.
     * </p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * logMetadataManager.withTags(Map.of("request_id", requestId), () -> {
     *     // All logs within this block will include request_id
     *     processRequest();
     * });
     * // request_id is automatically removed after the block
     * }</pre>
     *
     * @param temporaryTags tags to add temporarily
     * @param action        the action to execute
     */
    public void withTags(Map<String, Object> temporaryTags, Runnable action) {
        Objects.requireNonNull(temporaryTags, "Temporary tags must not be null");
        Objects.requireNonNull(action, "Action must not be null");

        try {
            addTags(temporaryTags);
            action.run();
        } finally {
            temporaryTags.keySet().forEach(this::removeTag);
        }
    }

    /**
     * Executes a block of code with temporary tags and returns a result.
     *
     * @param temporaryTags tags to add temporarily
     * @param action        the action to execute
     * @param <T>           the return type
     * @return the result of the action
     */
    public <T> T withTags(Map<String, Object> temporaryTags, Supplier<T> action) {
        Objects.requireNonNull(temporaryTags, "Temporary tags must not be null");
        Objects.requireNonNull(action, "Action must not be null");

        try {
            addTags(temporaryTags);
            return action.get();
        } finally {
            temporaryTags.keySet().forEach(this::removeTag);
        }
    }
}
