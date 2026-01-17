package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom JsonProvider for Logstash Logback Encoder that injects static tags
 * defined in YAML configuration into every JSON log line.
 * <p>
 * Static tags are defined at application startup via configuration properties
 * and remain constant throughout the application lifecycle. They are ideal for
 * environment-specific metadata like environment name, application version,
 * deployment region, etc.
 * </p>
 *
 * <h3>Configuration Example:</h3>
 * <pre>{@code
 * app:
 *   logging:
 *     static-tags:
 *       env: ${ENV_VAR:development}
 *       app_version: ${APP_VERSION:1.0.0}
 *       region: ${AWS_REGION:us-east-1}
 * }</pre>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@NoArgsConstructor
public class StaticTagsJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    /**
     * Static holder for the tags map.
     * Set once during configuration and never modified afterward.
     */
    @Getter
    private static volatile Map<String, String> staticTags = Collections.emptyMap();

    /**
     * Sets the static tags to be used by all StaticTagsJsonProvider instances.
     * <p>
     * This method is called by the auto-configuration when the Spring context is ready.
     * The provided map is copied to ensure immutability.
     * </p>
     *
     * @param tags the static tags map
     */
    public static void setStaticTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            staticTags = Collections.emptyMap();
        } else {
            staticTags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        }
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Map<String, String> tags = staticTags;
        
        if (tags.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            JsonWritingUtils.writeStringField(generator, entry.getKey(), entry.getValue());
        }
    }
}
