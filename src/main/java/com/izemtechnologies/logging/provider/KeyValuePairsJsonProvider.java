package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;
import org.slf4j.event.KeyValuePair;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Custom JsonProvider for Logstash Logback Encoder that properly handles
 * SLF4J 2.x fluent API key-value pairs.
 * <p>
 * This provider ensures that key-value pairs added via the SLF4J 2.x fluent API
 * (e.g., {@code logger.atInfo().addKeyValue("key", "value").log("msg")}) are
 * written as top-level JSON fields rather than being toString-ed.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // SLF4J 2.x fluent API
 * logger.atInfo()
 *     .addKeyValue("user_id", userId)
 *     .addKeyValue("action", "login")
 *     .addKeyValue("duration_ms", 150)
 *     .log("User action completed");
 *
 * // Produces JSON with top-level fields:
 * // {"message":"User action completed","user_id":"123","action":"login","duration_ms":150,...}
 * }</pre>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@NoArgsConstructor
public class KeyValuePairsJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    /**
     * Whether to include null values in the output.
     */
    @Getter
    @Setter
    private boolean includeNullValues = false;

    /**
     * Optional prefix for all key-value pair field names.
     */
    @Getter
    @Setter
    private String fieldPrefix = "";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
        
        if (keyValuePairs == null || keyValuePairs.isEmpty()) {
            return;
        }

        for (KeyValuePair kvp : keyValuePairs) {
            if (kvp == null || kvp.key == null) {
                continue;
            }

            Object value = kvp.value;
            
            if (value == null && !includeNullValues) {
                continue;
            }

            String fieldName = fieldPrefix.isEmpty() ? kvp.key : fieldPrefix + kvp.key;
            writeValue(generator, fieldName, value);
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
            writeMapField(generator, fieldName, mapValue);
        } else if (value instanceof Iterable<?> iterableValue) {
            writeIterableField(generator, fieldName, iterableValue);
        } else if (value.getClass().isArray()) {
            writeArrayField(generator, fieldName, value);
        } else {
            generator.writeObjectField(fieldName, value);
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
     * Writes a map field.
     */
    @SuppressWarnings("unchecked")
    private void writeMapField(JsonGenerator generator, String fieldName, Map<?, ?> value) throws IOException {
        generator.writeObjectFieldStart(fieldName);
        Map<String, Object> mapValue = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
            if (entry.getKey() != null) {
                writeValue(generator, entry.getKey(), entry.getValue());
            }
        }
        generator.writeEndObject();
    }

    /**
     * Writes an iterable field as a JSON array.
     */
    private void writeIterableField(JsonGenerator generator, String fieldName, Iterable<?> value) throws IOException {
        generator.writeArrayFieldStart(fieldName);
        for (Object item : value) {
            writeArrayValue(generator, item);
        }
        generator.writeEndArray();
    }

    /**
     * Writes an array field.
     */
    private void writeArrayField(JsonGenerator generator, String fieldName, Object array) throws IOException {
        generator.writeArrayFieldStart(fieldName);
        switch (array) {
            case Object[] objects -> {
                for (Object item : objects) {
                    writeArrayValue(generator, item);
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
            case float[] floats -> {
                for (float item : floats) {
                    generator.writeNumber(item);
                }
            }
            case short[] shorts -> {
                for (short item : shorts) {
                    generator.writeNumber(item);
                }
            }
            case byte[] bytes -> {
                for (byte item : bytes) {
                    generator.writeNumber(item);
                }
            }
            case char[] chars -> generator.writeString(new String(chars));
            default -> {
                // Unsupported array type
            }
        }
        generator.writeEndArray();
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
            generator.writeObject(value);
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
}
