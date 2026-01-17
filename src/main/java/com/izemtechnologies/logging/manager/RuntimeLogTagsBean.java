package com.izemtechnologies.logging.manager;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe singleton bean that manages dynamic runtime log tags.
 * <p>
 * This bean stores key-value pairs that will be automatically injected into
 * every JSON log line produced by the application. Tags can be added, removed,
 * or updated at runtime after the application has started.
 * </p>
 * <p>
 * Implementation uses {@link ConcurrentHashMap} for thread-safe operations
 * without requiring explicit synchronization.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Autowired
 * private RuntimeLogTagsBean runtimeTags;
 *
 * public void onTenantResolved(String tenantId) {
 *     runtimeTags.addTag("tenant_id", tenantId);
 * }
 * }</pre>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@Slf4j
@ToString
public class RuntimeLogTagsBean {

    /**
     * Thread-safe storage for dynamic tags.
     * Uses ConcurrentHashMap for lock-free reads and thread-safe writes.
     */
    private final ConcurrentHashMap<String, Object> tags = new ConcurrentHashMap<>();

    /**
     * Flag indicating whether this bean has been fully initialized.
     * Used for defensive coding in JsonProvider.
     */
    @Getter
    private volatile boolean initialized = false;

    /**
     * Marks this bean as fully initialized.
     * Called by the auto-configuration after Spring context is ready.
     */
    public void markInitialized() {
        this.initialized = true;
        log.debug("RuntimeLogTagsBean marked as initialized");
    }

    /**
     * Adds or updates a tag with the specified key and value.
     * <p>
     * If a tag with the same key already exists, its value will be replaced.
     * Both key and value must be non-null.
     * </p>
     *
     * @param key   the tag key (must not be null or empty)
     * @param value the tag value (must not be null)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if key is null/empty or value is null
     */
    public RuntimeLogTagsBean addTag(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "Tag value must not be null");
        tags.put(key, value);
        log.trace("Added tag: {}={}", key, value);
        return this;
    }

    /**
     * Adds multiple tags at once from the provided map.
     * <p>
     * Existing tags with the same keys will be replaced.
     * </p>
     *
     * @param tagsToAdd map of tags to add (must not be null)
     * @return this instance for method chaining
     * @throws IllegalArgumentException if any key is null/empty or any value is null
     */
    public RuntimeLogTagsBean addTags(Map<String, Object> tagsToAdd) {
        Objects.requireNonNull(tagsToAdd, "Tags map must not be null");
        tagsToAdd.forEach(this::addTag);
        return this;
    }

    /**
     * Removes a tag with the specified key.
     *
     * @param key the tag key to remove
     * @return the previous value associated with the key, or null if there was none
     */
    public Object removeTag(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Object removed = tags.remove(key);
        if (removed != null) {
            log.trace("Removed tag: {}", key);
        }
        return removed;
    }

    /**
     * Retrieves the value of a tag by its key.
     *
     * @param key the tag key
     * @return the tag value, or null if not found
     */
    public Object getTag(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return tags.get(key);
    }

    /**
     * Checks if a tag with the specified key exists.
     *
     * @param key the tag key to check
     * @return true if the tag exists, false otherwise
     */
    public boolean hasTag(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return tags.containsKey(key);
    }

    /**
     * Returns an unmodifiable view of all current tags.
     * <p>
     * The returned map is a snapshot and will not reflect subsequent changes.
     * This method is thread-safe and can be called from any thread.
     * </p>
     *
     * @return unmodifiable map of all tags
     */
    public Map<String, Object> getAllTags() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(tags));
    }

    /**
     * Returns a direct reference to the internal tags map for high-performance access.
     * <p>
     * <strong>Warning:</strong> This method is intended for internal use by the
     * JsonProvider only. External code should use {@link #getAllTags()} instead.
     * </p>
     *
     * @return direct reference to the internal ConcurrentHashMap
     */
    public ConcurrentHashMap<String, Object> getTagsReference() {
        return tags;
    }

    /**
     * Removes all tags from this bean.
     *
     * @return this instance for method chaining
     */
    public RuntimeLogTagsBean clearAllTags() {
        tags.clear();
        log.trace("Cleared all tags");
        return this;
    }

    /**
     * Returns the current number of tags.
     *
     * @return the number of tags
     */
    public int size() {
        return tags.size();
    }

    /**
     * Checks if there are no tags.
     *
     * @return true if no tags exist, false otherwise
     */
    public boolean isEmpty() {
        return tags.isEmpty();
    }

    /**
     * Validates that the key is not null or empty.
     *
     * @param key the key to validate
     * @throws IllegalArgumentException if key is null or blank
     */
    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Tag key must not be null or empty");
        }
    }
}
