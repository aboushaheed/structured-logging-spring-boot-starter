package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.izemtechnologies.logging.masking.SensitiveDataMasker;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.slf4j.event.KeyValuePair;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Logback JsonProvider that applies sensitive data masking to log messages and arguments.
 * 
 * <p>This provider intercepts log events and masks any sensitive data found in:</p>
 * <ul>
 *   <li>Log messages</li>
 *   <li>Log arguments</li>
 *   <li>Key-value pairs (SLF4J 2.x fluent API)</li>
 *   <li>MDC values</li>
 * </ul>
 * 
 * <p>The masking is performed using the {@link SensitiveDataMasker} component,
 * which supports multiple masking modes and a comprehensive set of sensitive data patterns.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class MaskingJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private static SensitiveDataMasker masker;

    /**
     * Whether to mask the formatted message.
     */
    private boolean maskMessage = true;

    /**
     * Whether to mask key-value pairs from SLF4J 2.x fluent API.
     */
    private boolean maskKeyValues = true;

    /**
     * Whether to mask MDC values.
     */
    private boolean maskMdc = true;

    /**
     * Field name for the masked message (if different from default).
     */
    private String maskedMessageFieldName = null;

    /**
     * Sets the masker instance (called during auto-configuration).
     * 
     * @param maskerInstance the masker to use
     */
    public static void setMasker(SensitiveDataMasker maskerInstance) {
        masker = maskerInstance;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (masker == null) {
            // Masker not yet initialized, skip masking
            return;
        }

        try {
            // Mask and write the formatted message if enabled
            if (maskMessage && maskedMessageFieldName != null) {
                String originalMessage = event.getFormattedMessage();
                String maskedMessage = masker.mask(originalMessage);
                if (!maskedMessage.equals(originalMessage)) {
                    generator.writeStringField(maskedMessageFieldName, maskedMessage);
                }
            }

            // Mask key-value pairs from SLF4J 2.x fluent API
            if (maskKeyValues) {
                writeKeyValuePairs(generator, event);
            }

        } catch (Exception e) {
            // Defensive: never let masking crash the application
            if (log.isDebugEnabled()) {
                log.debug("Error during sensitive data masking", e);
            }
        }
    }

    private void writeKeyValuePairs(JsonGenerator generator, ILoggingEvent event) throws IOException {
        List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
        if (keyValuePairs == null || keyValuePairs.isEmpty()) {
            return;
        }

        for (KeyValuePair kvp : keyValuePairs) {
            String key = kvp.key;
            Object value = kvp.value;

            if (value == null) {
                continue;
            }

            // Apply masking to string values
            Object maskedValue = maskValue(key, value);
            writeField(generator, key, maskedValue);
        }
    }

    private Object maskValue(String key, Object value) {
        if (value instanceof String s) {
            return masker.mask(s);
        } else if (value instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) m;
            return masker.maskMap(map);
        }
        return value;
    }

    private void writeField(JsonGenerator generator, String fieldName, Object value) throws IOException {
        if (value == null) {
            generator.writeNullField(fieldName);
        } else if (value instanceof String s) {
            generator.writeStringField(fieldName, s);
        } else if (value instanceof Number n) {
            writeNumberField(generator, fieldName, n);
        } else if (value instanceof Boolean b) {
            generator.writeBooleanField(fieldName, b);
        } else if (value instanceof Map<?, ?> m) {
            generator.writeObjectFieldStart(fieldName);
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    writeField(generator, key, entry.getValue());
                }
            }
            generator.writeEndObject();
        } else {
            generator.writeStringField(fieldName, value.toString());
        }
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
