package com.izemtechnologies.logging;

import com.izemtechnologies.logging.manager.LogMetadataManager;
import com.izemtechnologies.logging.manager.RuntimeLogTagsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogMetadataManager.
 */
class LogMetadataManagerTest {

    private RuntimeLogTagsBean runtimeTags;
    private LogMetadataManager manager;

    @BeforeEach
    void setUp() {
        runtimeTags = new RuntimeLogTagsBean();
        manager = new LogMetadataManager(runtimeTags);
    }

    @Test
    @DisplayName("Should add and retrieve tags")
    void shouldAddAndRetrieveTags() {
        manager.addTag("tenant_id", "t-123");
        
        assertEquals("t-123", manager.getTag("tenant_id"));
        assertTrue(manager.hasTag("tenant_id"));
    }

    @Test
    @DisplayName("Should support fluent method chaining")
    void shouldSupportFluentMethodChaining() {
        manager.addTag("key1", "value1")
               .addTag("key2", "value2")
               .addTag("key3", "value3");
        
        assertEquals(3, manager.getTagCount());
    }

    @Test
    @DisplayName("Should remove tags")
    void shouldRemoveTags() {
        manager.addTag("key1", "value1")
               .addTag("key2", "value2");
        
        manager.removeTag("key1");
        
        assertFalse(manager.hasTag("key1"));
        assertTrue(manager.hasTag("key2"));
    }

    @Test
    @DisplayName("Should remove multiple tags at once")
    void shouldRemoveMultipleTags() {
        manager.addTag("key1", "value1")
               .addTag("key2", "value2")
               .addTag("key3", "value3");
        
        manager.removeTags("key1", "key2");
        
        assertFalse(manager.hasTag("key1"));
        assertFalse(manager.hasTag("key2"));
        assertTrue(manager.hasTag("key3"));
    }

    @Test
    @DisplayName("Should add tags from map")
    void shouldAddTagsFromMap() {
        manager.addTags(Map.of(
            "env", "production",
            "region", "us-east-1"
        ));
        
        assertEquals("production", manager.getTag("env"));
        assertEquals("us-east-1", manager.getTag("region"));
    }

    @Test
    @DisplayName("Should clear all tags")
    void shouldClearAllTags() {
        manager.addTag("key1", "value1")
               .addTag("key2", "value2");
        
        manager.clearAllTags();
        
        assertTrue(manager.isEmpty());
    }

    @Test
    @DisplayName("Should get tag with type casting")
    void shouldGetTagWithTypeCasting() {
        manager.addTag("count", 42);
        manager.addTag("name", "test");
        
        Integer count = manager.getTag("count", Integer.class);
        String name = manager.getTag("name", String.class);
        
        assertEquals(42, count);
        assertEquals("test", name);
    }

    @Test
    @DisplayName("Should return null for non-existent tag")
    void shouldReturnNullForNonExistentTag() {
        assertNull(manager.getTag("non_existent"));
        assertNull(manager.getTag("non_existent", String.class));
    }

    @Test
    @DisplayName("Should execute runnable with temporary tags")
    void shouldExecuteRunnableWithTemporaryTags() {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        manager.withTags(Map.of("temp_key", "temp_value"), () -> {
            assertTrue(manager.hasTag("temp_key"));
            assertEquals("temp_value", manager.getTag("temp_key"));
            executed.set(true);
        });
        
        assertTrue(executed.get());
        assertFalse(manager.hasTag("temp_key")); // Tag should be removed
    }

    @Test
    @DisplayName("Should execute supplier with temporary tags and return result")
    void shouldExecuteSupplierWithTemporaryTags() {
        String result = manager.withTags(
            Map.of("request_id", "req-123"),
            () -> {
                assertTrue(manager.hasTag("request_id"));
                return "processed";
            }
        );
        
        assertEquals("processed", result);
        assertFalse(manager.hasTag("request_id")); // Tag should be removed
    }

    @Test
    @DisplayName("Should clean up temporary tags even on exception")
    void shouldCleanUpTemporaryTagsOnException() {
        try {
            manager.withTags(Map.of("temp", "value"), () -> {
                assertTrue(manager.hasTag("temp"));
                throw new RuntimeException("Test exception");
            });
            fail("Should have thrown exception");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
        }
        
        assertFalse(manager.hasTag("temp")); // Tag should still be removed
    }

    @Test
    @DisplayName("Should support lazy value computation")
    void shouldSupportLazyValueComputation() {
        AtomicReference<String> computedValue = new AtomicReference<>();
        
        manager.addTag("lazy_key", () -> {
            computedValue.set("computed");
            return "lazy_value";
        });
        
        assertEquals("lazy_value", manager.getTag("lazy_key"));
        assertEquals("computed", computedValue.get());
    }

    @Test
    @DisplayName("Should preserve existing tags when using withTags")
    void shouldPreserveExistingTagsWhenUsingWithTags() {
        manager.addTag("permanent", "value");
        
        manager.withTags(Map.of("temporary", "temp_value"), () -> {
            assertTrue(manager.hasTag("permanent"));
            assertTrue(manager.hasTag("temporary"));
        });
        
        assertTrue(manager.hasTag("permanent")); // Should still exist
        assertFalse(manager.hasTag("temporary")); // Should be removed
    }

    @Test
    @DisplayName("Should return all tags as unmodifiable map")
    void shouldReturnAllTagsAsUnmodifiableMap() {
        manager.addTag("key1", "value1")
               .addTag("key2", "value2");
        
        Map<String, Object> allTags = manager.getAllTags();
        
        assertEquals(2, allTags.size());
        assertThrows(UnsupportedOperationException.class, () -> {
            allTags.put("new_key", "new_value");
        });
    }

    @Test
    @DisplayName("Should provide access to underlying RuntimeLogTagsBean")
    void shouldProvideAccessToUnderlyingBean() {
        RuntimeLogTagsBean underlyingBean = manager.getRuntimeTags();
        
        assertSame(runtimeTags, underlyingBean);
    }

    @Test
    @DisplayName("Should report isEmpty correctly")
    void shouldReportIsEmptyCorrectly() {
        assertTrue(manager.isEmpty());
        
        manager.addTag("key", "value");
        assertFalse(manager.isEmpty());
        
        manager.removeTag("key");
        assertTrue(manager.isEmpty());
    }

    @Test
    @DisplayName("Should report tagCount correctly")
    void shouldReportTagCountCorrectly() {
        assertEquals(0, manager.getTagCount());
        
        manager.addTag("key1", "value1");
        assertEquals(1, manager.getTagCount());
        
        manager.addTag("key2", "value2");
        assertEquals(2, manager.getTagCount());
        
        manager.removeTag("key1");
        assertEquals(1, manager.getTagCount());
    }
}
