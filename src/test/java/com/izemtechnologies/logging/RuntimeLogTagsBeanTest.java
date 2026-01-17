package com.izemtechnologies.logging;

import com.izemtechnologies.logging.manager.RuntimeLogTagsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuntimeLogTagsBean.
 */
class RuntimeLogTagsBeanTest {

    private RuntimeLogTagsBean bean;

    @BeforeEach
    void setUp() {
        bean = new RuntimeLogTagsBean();
    }

    @Test
    @DisplayName("Should add and retrieve a tag")
    void shouldAddAndRetrieveTag() {
        bean.addTag("tenant_id", "t-123");
        
        assertEquals("t-123", bean.getTag("tenant_id"));
        assertTrue(bean.hasTag("tenant_id"));
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportMethodChaining() {
        bean.addTag("key1", "value1")
            .addTag("key2", "value2")
            .addTag("key3", "value3");
        
        assertEquals(3, bean.size());
        assertEquals("value1", bean.getTag("key1"));
        assertEquals("value2", bean.getTag("key2"));
        assertEquals("value3", bean.getTag("key3"));
    }

    @Test
    @DisplayName("Should remove a tag")
    void shouldRemoveTag() {
        bean.addTag("key", "value");
        assertTrue(bean.hasTag("key"));
        
        Object removed = bean.removeTag("key");
        
        assertEquals("value", removed);
        assertFalse(bean.hasTag("key"));
        assertNull(bean.getTag("key"));
    }

    @Test
    @DisplayName("Should add multiple tags at once")
    void shouldAddMultipleTags() {
        bean.addTags(Map.of(
            "env", "production",
            "region", "us-east-1",
            "cluster", "c-123"
        ));
        
        assertEquals(3, bean.size());
        assertEquals("production", bean.getTag("env"));
        assertEquals("us-east-1", bean.getTag("region"));
        assertEquals("c-123", bean.getTag("cluster"));
    }

    @Test
    @DisplayName("Should clear all tags")
    void shouldClearAllTags() {
        bean.addTag("key1", "value1")
            .addTag("key2", "value2");
        
        assertEquals(2, bean.size());
        
        bean.clearAllTags();
        
        assertTrue(bean.isEmpty());
        assertEquals(0, bean.size());
    }

    @Test
    @DisplayName("Should return unmodifiable copy of all tags")
    void shouldReturnUnmodifiableCopy() {
        bean.addTag("key", "value");
        
        Map<String, Object> allTags = bean.getAllTags();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            allTags.put("new_key", "new_value");
        });
    }

    @Test
    @DisplayName("Should handle null key gracefully")
    void shouldHandleNullKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            bean.addTag(null, "value");
        });
        
        assertNull(bean.getTag(null));
        assertFalse(bean.hasTag(null));
        assertNull(bean.removeTag(null));
    }

    @Test
    @DisplayName("Should handle empty key gracefully")
    void shouldHandleEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            bean.addTag("", "value");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            bean.addTag("   ", "value");
        });
    }

    @Test
    @DisplayName("Should reject null value")
    void shouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> {
            bean.addTag("key", null);
        });
    }

    @Test
    @DisplayName("Should support different value types")
    void shouldSupportDifferentValueTypes() {
        bean.addTag("string", "text")
            .addTag("integer", 42)
            .addTag("long", 123456789L)
            .addTag("double", 3.14159)
            .addTag("boolean", true)
            .addTag("list", java.util.List.of("a", "b", "c"));
        
        assertEquals("text", bean.getTag("string"));
        assertEquals(42, bean.getTag("integer"));
        assertEquals(123456789L, bean.getTag("long"));
        assertEquals(3.14159, bean.getTag("double"));
        assertEquals(true, bean.getTag("boolean"));
        assertEquals(java.util.List.of("a", "b", "c"), bean.getTag("list"));
    }

    @Test
    @DisplayName("Should track initialization state")
    void shouldTrackInitializationState() {
        assertFalse(bean.isInitialized());
        
        bean.markInitialized();
        
        assertTrue(bean.isInitialized());
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent operations")
    void shouldBeThreadSafeForConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "thread_" + threadId + "_key_" + i;
                        bean.addTag(key, "value_" + i);
                        bean.getTag(key);
                        bean.hasTag(key);
                        bean.removeTag(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertTrue(completed, "All threads should complete within timeout");
        // After all operations, bean should be empty (all tags removed)
        assertTrue(bean.isEmpty());
    }

    @Test
    @DisplayName("Should provide direct reference for high-performance access")
    void shouldProvideDirectReference() {
        bean.addTag("key", "value");
        
        var reference = bean.getTagsReference();
        
        assertNotNull(reference);
        assertEquals("value", reference.get("key"));
        
        // Direct modification should be reflected
        reference.put("direct_key", "direct_value");
        assertEquals("direct_value", bean.getTag("direct_key"));
    }
}
