package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.izemtechnologies.logging.context.RequestScopedLogContext;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Logback JsonProvider that injects request-scoped tags into JSON log output.
 * 
 * <p>This provider reads tags from {@link RequestScopedLogContext} and writes
 * them as top-level fields in the JSON log event. Tags are isolated per request
 * thread, ensuring no cross-contamination between concurrent requests.</p>
 * 
 * <p>Configuration options:</p>
 * <ul>
 *   <li>{@code prefix} - Optional prefix to add to all field names</li>
 *   <li>{@code nested} - If true, nest all tags under a single field</li>
 *   <li>{@code nestedFieldName} - Field name when nesting (default: "request")</li>
 *   <li>{@code excludeFields} - Fields to exclude from output</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class RequestScopedTagsJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    /**
     * Optional prefix to add to all request-scoped tag field names.
     */
    private String prefix = "";

    /**
     * Whether to nest all request-scoped tags under a single field.
     */
    private boolean nested = false;

    /**
     * Field name to use when nesting is enabled.
     */
    private String nestedFieldName = "request";

    /**
     * Fields to exclude from output.
     */
    private List<String> excludeFields = List.of();

    /**
     * Maximum number of tags to output (for safety).
     */
    private int maxTags = 50;

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Map<String, Object> tags = RequestScopedLogContext.getReference();
        
        if (tags == null || tags.isEmpty()) {
            return;
        }

        try {
            if (nested) {
                writeNestedTags(generator, tags);
            } else {
                writeFlatTags(generator, tags);
            }
        } catch (Exception e) {
            // Defensive: never let logging provider crash the application
            if (log.isDebugEnabled()) {
                log.debug("Error writing request-scoped tags to JSON", e);
            }
        }
    }

    private void writeFlatTags(JsonGenerator generator, Map<String, Object> tags) throws IOException {
        int count = 0;
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (count >= maxTags) {
                generator.writeStringField(prefix + "_truncated", "true");
                break;
            }
            
            String key = entry.getKey();
            if (shouldExclude(key)) {
                continue;
            }
            
            String fieldName = prefix.isEmpty() ? key : prefix + key;
            writeValue(generator, fieldName, entry.getValue());
            count++;
        }
    }

    private void writeNestedTags(JsonGenerator generator, Map<String, Object> tags) throws IOException {
        generator.writeObjectFieldStart(nestedFieldName);
        
        int count = 0;
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (count >= maxTags) {
                generator.writeStringField("_truncated", "true");
                break;
            }
            
            String key = entry.getKey();
            if (shouldExclude(key)) {
                continue;
            }
            
            String fieldName = prefix.isEmpty() ? key : prefix + key;
            writeValue(generator, fieldName, entry.getValue());
            count++;
        }
        
        generator.writeEndObject();
    }

    private void writeValue(JsonGenerator generator, String fieldName, Object value) throws IOException {
        if (value == null) {
            generator.writeNullField(fieldName);
        } else if (value instanceof String s) {
            generator.writeStringField(fieldName, s);
        } else if (value instanceof Number n) {
            writeNumberField(generator, fieldName, n);
        } else if (value instanceof Boolean b) {
            generator.writeBooleanField(fieldName, b);
        } else if (value instanceof Collection<?> c) {
            generator.writeArrayFieldStart(fieldName);
            for (Object item : c) {
                writeArrayValue(generator, item);
            }
            generator.writeEndArray();
        } else if (value instanceof Map<?, ?> m) {
            generator.writeObjectFieldStart(fieldName);
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    writeValue(generator, key, entry.getValue());
                }
            }
            generator.writeEndObject();
        } else {
            // Fallback: convert to string
            generator.writeStringField(fieldName, value.toString());
        }
    }

    private void writeArrayValue(JsonGenerator generator, Object value) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else if (value instanceof String s) {
            generator.writeString(s);
        } else if (value instanceof Number n) {
            if (n instanceof Integer || n instanceof Long) {
                generator.writeNumber(n.longValue());
            } else {
                generator.writeNumber(n.doubleValue());
            }
        } else if (value instanceof Boolean b) {
            generator.writeBoolean(b);
        } else {
            generator.writeString(value.toString());
        }
    }

    private boolean shouldExclude(String key) {
        return excludeFields.contains(key);
    }

    private void writeNumberField(JsonGenerator generator, String fieldName, Number value) throws IOException {
        switch (value) {
            case Integer i -> generator.writeNumberField(fieldName, i);
            case Long l -> generator.writeNumberField(fieldName, l);
            case Double d -> generator.writeNumberField(fieldName, d);
            case Float f -> generator.writeNumberField(fieldName, f);
            case Short s -> generator.writeNumberField(fieldName, s.intValue());
            case Byte b -> generator.writeNumberField(fieldName, b.intValue());
            default -> generator.writeNumberField(fieldName, value.doubleValue());
        }
    }
}
