package com.izemtechnologies.logging.config;

import com.izemtechnologies.logging.properties.LoggingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.zalando.logbook.*;
import org.zalando.logbook.core.BodyFilters;
import org.zalando.logbook.core.Conditions;
import org.zalando.logbook.core.DefaultHttpLogFormatter;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.json.JsonBodyFilters;
import org.zalando.logbook.logstash.LogstashLogbackSink;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.zalando.logbook.core.Conditions.*;

/**
 * Auto-configuration for Zalando Logbook HTTP traffic logging.
 * <p>
 * This configuration sets up Logbook to capture HTTP request/response traffic
 * and route it through the main SLF4J logger with JSON formatting.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *     <li>Request/response body capture</li>
 *     <li>Header masking for sensitive data</li>
 *     <li>JSON body path masking</li>
 *     <li>Path exclusion patterns</li>
 *     <li>Integration with Logstash Logback Encoder</li>
 * </ul>
 *
 * @author Logging Starter Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.zalando.logbook.Logbook")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "app.logging.logbook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogbookAutoConfiguration {

    /**
     * Creates the Logbook instance with configured filters and sink.
     *
     * @param properties the logging properties
     * @return the configured Logbook instance
     */
    @Bean
    @ConditionalOnMissingBean
    public Logbook logbook(LoggingProperties properties) {
        LoggingProperties.LogbookProperties logbookProps = properties.getLogbook();
        
        log.debug("Configuring Logbook with {} masked headers and {} masked body paths",
                logbookProps.getMaskedHeaders().size(),
                logbookProps.getMaskedBodyPaths().size());

        return Logbook.builder()
                .condition(buildCondition(logbookProps))
                .headerFilter(buildHeaderFilter(logbookProps))
                .bodyFilter(buildBodyFilter(logbookProps))
                .sink(buildSink(logbookProps))
                .build();
    }

    /**
     * Builds the condition for filtering which requests to log.
     */
    private Predicate<HttpRequest> buildCondition(LoggingProperties.LogbookProperties props) {
        Predicate<HttpRequest> condition = exclude(
                requestTo("/actuator/**"),
                requestTo("/health/**"),
                requestTo("/favicon.ico")
        );

        // Add custom exclude paths
        Set<String> excludePaths = props.getExcludePaths();
        if (excludePaths != null && !excludePaths.isEmpty()) {
            for (String path : excludePaths) {
                condition = condition.and(exclude(requestTo(path)));
            }
        }

        return condition;
    }

    /**
     * Builds the header filter for masking sensitive headers.
     */
    private HeaderFilter buildHeaderFilter(LoggingProperties.LogbookProperties props) {
        Set<String> maskedHeaders = props.getMaskedHeaders();
        
        if (maskedHeaders == null || maskedHeaders.isEmpty()) {
            return HeaderFilter.none();
        }

        return HeaderFilters.replaceHeaders(
                maskedHeaders,
                "***MASKED***"
        );
    }

    /**
     * Builds the body filter for masking sensitive JSON paths.
     */
    private BodyFilter buildBodyFilter(LoggingProperties.LogbookProperties props) {
        Set<String> maskedPaths = props.getMaskedBodyPaths();
        
        if (maskedPaths == null || maskedPaths.isEmpty()) {
            return BodyFilter.none();
        }

        // Combine multiple JSON path filters
        BodyFilter combinedFilter = BodyFilter.none();
        
        for (String path : maskedPaths) {
            // Convert JsonPath to property name for simple cases
            String propertyName = extractPropertyName(path);
            if (propertyName != null) {
                combinedFilter = BodyFilter.merge(
                        combinedFilter,
                        JsonBodyFilters.replaceJsonStringProperty(
                                value -> propertyName.equalsIgnoreCase(value),
                                "***MASKED***"
                        )
                );
            }
        }

        return combinedFilter;
    }

    /**
     * Extracts property name from a simple JsonPath expression.
     * Supports patterns like $.password, $.secret, etc.
     */
    private String extractPropertyName(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return null;
        }
        
        // Handle simple paths like $.password or $['password']
        if (jsonPath.startsWith("$.")) {
            return jsonPath.substring(2);
        } else if (jsonPath.startsWith("$['") && jsonPath.endsWith("']")) {
            return jsonPath.substring(3, jsonPath.length() - 2);
        }
        
        return jsonPath;
    }

    /**
     * Builds the sink for writing log output.
     * Uses LogstashLogbackSink for JSON integration.
     */
    private Sink buildSink(LoggingProperties.LogbookProperties props) {
        // Use LogstashLogbackSink for seamless JSON integration
        return new LogstashLogbackSink(
                new DefaultHttpLogFormatter()
        );
    }

    /**
     * Creates a custom HttpLogWriter that routes to SLF4J.
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpLogWriter httpLogWriter() {
        return new Slf4jHttpLogWriter();
    }

    /**
     * Custom HttpLogWriter implementation that writes to SLF4J.
     */
    @Slf4j
    public static class Slf4jHttpLogWriter implements HttpLogWriter {

        private static final org.slf4j.Logger HTTP_LOGGER = 
                org.slf4j.LoggerFactory.getLogger("http.traffic");

        @Override
        public boolean isActive() {
            return HTTP_LOGGER.isInfoEnabled();
        }

        @Override
        public void write(Precorrelation precorrelation, String request) {
            HTTP_LOGGER.atInfo()
                    .addKeyValue("correlation_id", precorrelation.getId())
                    .addKeyValue("type", "request")
                    .log(request);
        }

        @Override
        public void write(Correlation correlation, String response) {
            HTTP_LOGGER.atInfo()
                    .addKeyValue("correlation_id", correlation.getId())
                    .addKeyValue("type", "response")
                    .addKeyValue("duration_ms", correlation.getDuration().toMillis())
                    .log(response);
        }
    }
}
