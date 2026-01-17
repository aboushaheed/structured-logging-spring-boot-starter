package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.izemtechnologies.logging.enrichment.LogEnricher;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;
import java.util.*;

/**
 * Logback JsonProvider that applies all registered LogEnrichers to log events.
 * 
 * <p>This provider collects data from all registered enrichers and adds them
 * to the JSON output. Enrichers are called in order of their priority.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class LogEnrichmentJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private static List<LogEnricher> enrichers = new ArrayList<>();

    /**
     * Whether to nest enrichment fields under a parent object.
     */
    private boolean nested = false;

    /**
     * Parent field name when nested is true.
     */
    private String nestedFieldName = "context";

    /**
     * Sets the enrichers (called during auto-configuration).
     * 
     * @param enricherList the list of enrichers
     */
    public static void setEnrichers(List<LogEnricher> enricherList) {
        if (enricherList != null) {
            enrichers = new ArrayList<>(enricherList);
            // Sort by order
            enrichers.sort(Comparator.comparingInt(LogEnricher::getOrder));
        }
    }

    /**
     * Adds an enricher dynamically.
     * 
     * @param enricher the enricher to add
     */
    public static void addEnricher(LogEnricher enricher) {
        enrichers.add(enricher);
        enrichers.sort(Comparator.comparingInt(LogEnricher::getOrder));
    }

    /**
     * Removes an enricher by name.
     * 
     * @param name the enricher name
     */
    public static void removeEnricher(String name) {
        enrichers.removeIf(e -> e.getName().equals(name));
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        if (enrichers.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> allFields = collectEnrichmentData();

            if (allFields.isEmpty()) {
                return;
            }

            if (nested) {
                generator.writeObjectFieldStart(nestedFieldName);
                writeFields(generator, allFields);
                generator.writeEndObject();
            } else {
                writeFields(generator, allFields);
            }

        } catch (Exception e) {
            // Defensive: never let enrichment crash the application
            if (log.isDebugEnabled()) {
                log.debug("Error during log enrichment", e);
            }
        }
    }

    private Map<String, Object> collectEnrichmentData() {
        Map<String, Object> allFields = new LinkedHashMap<>();

        for (LogEnricher enricher : enrichers) {
            try {
                if (enricher.isActive()) {
                    Map<String, Object> fields = enricher.enrich();
                    if (fields != null && !fields.isEmpty()) {
                        allFields.putAll(fields);
                    }
                }
            } catch (Exception e) {
                log.debug("Enricher {} failed: {}", enricher.getName(), e.getMessage());
            }
        }

        return allFields;
    }

    private void writeFields(JsonGenerator generator, Map<String, Object> fields) throws IOException {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            writeField(generator, entry.getKey(), entry.getValue());
        }
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
        } else if (value instanceof Collection<?> c) {
            generator.writeArrayFieldStart(fieldName);
            for (Object item : c) {
                writeValue(generator, item);
            }
            generator.writeEndArray();
        } else {
            generator.writeStringField(fieldName, value.toString());
        }
    }

    private void writeValue(JsonGenerator generator, Object value) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else if (value instanceof String s) {
            generator.writeString(s);
        } else if (value instanceof Number n) {
            writeNumberValue(generator, n);
        } else if (value instanceof Boolean b) {
            generator.writeBoolean(b);
        } else {
            generator.writeString(value.toString());
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

    private void writeNumberValue(JsonGenerator generator, Number value) throws IOException {
        switch (value) {
            case Integer i -> generator.writeNumber(i);
            case Long l -> generator.writeNumber(l);
            case Double d -> generator.writeNumber(d);
            case Float f -> generator.writeNumber(f);
            default -> generator.writeNumber(value.doubleValue());
        }
    }
}
