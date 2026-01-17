package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.izemtechnologies.logging.manager.RuntimeLogTagsBean;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom JsonProvider for Logstash Logback Encoder that injects dynamic runtime tags
 * into every JSON log line.
 * <p>
 * This provider reads tags from the {@link RuntimeLogTagsBean} and writes them as
 * top-level fields in the JSON output. It handles the case where the Spring Context
 * is not yet fully ready through defensive coding.
 * </p>
 *
 * <h3>Integration:</h3>
 * <p>
 * This provider is automatically registered with the LogstashEncoder by the
 * auto-configuration. Tags added via {@link com.izemtechnologies.logging.manager.LogMetadataManager}
 * will appear in all subsequent log lines.
 * </p>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe. It reads from a ConcurrentHashMap and performs
 * no modifications to shared state during logging operations.
 * </p>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@NoArgsConstructor
public class DynamicTagsJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    /**
     * Static holder for the RuntimeLogTagsBean reference.
     * Using a static holder pattern to allow the provider to access the bean
     * even when created by Logback before Spring context is ready.
     */
    @Getter
    private static volatile RuntimeLogTagsBean runtimeTagsBean;

    /**
     * Fallback empty map used when the bean is not yet available.
     */
    private static final ConcurrentHashMap<String, Object> EMPTY_MAP = new ConcurrentHashMap<>();

    /**
     * Field name prefix for dynamic tags (optional).
     * If set, all dynamic tags will be prefixed with this value.
     */
    @Getter
    @Setter
    private String tagPrefix = "";

    /**
     * Whether to nest dynamic tags under a single field.
     */
    @Getter
    @Setter
    private boolean nestTags = false;

    /**
     * Field name to use when nesting tags.
     */
    @Getter
    @Setter
    private String nestedFieldName = "dynamic_tags";

    /**
     * Sets the RuntimeLogTagsBean instance to be used by all DynamicTagsJsonProvider instances.
     * <p>
     * This method is called by the auto-configuration when the Spring context is ready.
     * </p>
     *
     * @param bean the RuntimeLogTagsBean instance
     */
    public static void setRuntimeTagsBean(RuntimeLogTagsBean bean) {
        runtimeTagsBean = bean;
    }

    /**
     * Gets the tags map, with defensive handling for uninitialized state.
     *
     * @return the tags map, or an empty map if not ready
     */
    private ConcurrentHashMap<String, Object> getTagsSafely() {
        RuntimeLogTagsBean bean = runtimeTagsBean;
        if (bean == null) {
            return EMPTY_MAP;
        }
        ConcurrentHashMap<String, Object> tags = bean.getTagsReference();
        return tags != null ? tags : EMPTY_MAP;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        ConcurrentHashMap<String, Object> tags = getTagsSafely();
        
        if (tags.isEmpty()) {
            return;
        }

        if (nestTags) {
            generator.writeObjectFieldStart(nestedFieldName);
            writeTags(generator, tags);
            generator.writeEndObject();
        } else {
            writeTags(generator, tags);
        }
    }

    /**
     * Writes all tags to the JSON generator.
     */
    private void writeTags(JsonGenerator generator, Map<String, Object> tags) throws IOException {
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String fieldName = tagPrefix.isEmpty() ? entry.getKey() : tagPrefix + entry.getKey();
            writeValue(generator, fieldName, entry.getValue());
        }
    }

    /**
     * Writes a single value to the JSON generator with proper type handling.
     */
    private void writeValue(JsonGenerator generator, String fieldName, Object value) throws IOException {
        if (value == null) {
            generator.writeNullField(fieldName);
        } else if (value instanceof String stringValue) {
            JsonWritingUtils.writeStringField(generator, fieldName, stringValue);
        } else if (value instanceof Number numberValue) {
            writeNumberField(generator, fieldName, numberValue);
        } else if (value instanceof Boolean boolValue) {
            generator.writeBooleanField(fieldName, boolValue);
        } else if (value instanceof Map<?, ?> mapValue) {
            generator.writeObjectFieldStart(fieldName);
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) mapValue;
            for (Map.Entry<String, Object> entry : typedMap.entrySet()) {
                writeValue(generator, entry.getKey(), entry.getValue());
            }
            generator.writeEndObject();
        } else if (value instanceof Iterable<?> iterableValue) {
            generator.writeArrayFieldStart(fieldName);
            for (Object item : iterableValue) {
                writeArrayValue(generator, item);
            }
            generator.writeEndArray();
        } else if (value.getClass().isArray()) {
            generator.writeArrayFieldStart(fieldName);
            writeArrayValues(generator, value);
            generator.writeEndArray();
        } else {
            JsonWritingUtils.writeStringField(generator, fieldName, value.toString());
        }
    }

    /**
     * Writes a number field with proper type handling.
     */
    private void writeNumberField(JsonGenerator generator, String fieldName, Number value) throws IOException {
        switch (value) {
            case Integer i -> generator.writeNumberField(fieldName, i);
            case Long l -> generator.writeNumberField(fieldName, l);
            case Double d -> generator.writeNumberField(fieldName, d);
            case Float f -> generator.writeNumberField(fieldName, f);
            case Short s -> generator.writeNumberField(fieldName, s);
            case Byte b -> generator.writeNumberField(fieldName, b);
            default -> generator.writeNumberField(fieldName, value.doubleValue());
        }
    }

    /**
     * Writes a single value to a JSON array.
     */
    private void writeArrayValue(JsonGenerator generator, Object value) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else if (value instanceof String stringValue) {
            generator.writeString(stringValue);
        } else if (value instanceof Number numberValue) {
            writeArrayNumber(generator, numberValue);
        } else if (value instanceof Boolean boolValue) {
            generator.writeBoolean(boolValue);
        } else {
            generator.writeString(value.toString());
        }
    }

    /**
     * Writes a number to a JSON array.
     */
    private void writeArrayNumber(JsonGenerator generator, Number value) throws IOException {
        switch (value) {
            case Integer i -> generator.writeNumber(i);
            case Long l -> generator.writeNumber(l);
            case Double d -> generator.writeNumber(d);
            case Float f -> generator.writeNumber(f);
            default -> generator.writeNumber(value.doubleValue());
        }
    }

    /**
     * Writes array values handling different array types.
     */
    private void writeArrayValues(JsonGenerator generator, Object array) throws IOException {
        switch (array) {
            case String[] strings -> {
                for (String item : strings) {
                    generator.writeString(item);
                }
            }
            case int[] ints -> {
                for (int item : ints) {
                    generator.writeNumber(item);
                }
            }
            case long[] longs -> {
                for (long item : longs) {
                    generator.writeNumber(item);
                }
            }
            case double[] doubles -> {
                for (double item : doubles) {
                    generator.writeNumber(item);
                }
            }
            case boolean[] booleans -> {
                for (boolean item : booleans) {
                    generator.writeBoolean(item);
                }
            }
            case Object[] objects -> {
                for (Object item : objects) {
                    writeArrayValue(generator, item);
                }
            }
            default -> {
                // Unsupported array type
            }
        }
    }
}
